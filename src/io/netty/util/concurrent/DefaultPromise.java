/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import io.netty.util.Signal;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.*;

public class DefaultPromise<V> extends AbstractFuture<V> implements Promise<V> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultPromise.class);
    private static final InternalLogger rejectedExecutionLogger =
            InternalLoggerFactory.getInstance(DefaultPromise.class.getName() + ".rejectedExecution");

    private static final int MAX_LISTENER_STACK_DEPTH = 8;
    private static final Signal SUCCESS = Signal.valueOf(DefaultPromise.class, "SUCCESS");
    private static final Signal UNCANCELLABLE = Signal.valueOf(DefaultPromise.class, "UNCANCELLABLE");
    private static final CauseHolder CANCELLATION_CAUSE_HOLDER = new CauseHolder(new CancellationException());

    static {
        CANCELLATION_CAUSE_HOLDER.cause.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    EventExecutor executor;

    private volatile Object result;

    /**
     * One or more listeners. Can be a {@link GenericFutureListener} or a {@link DefaultFutureListeners}.
     * If {@code null}, it means either 1) no listeners were added yet or 2) all listeners were notified.
     */
    //多个监听器，如果为null,说明1)还没有监听器添加；2)所以的监听器已经被通知
    private Object listeners;

    /**
     * The list of the listeners that were added after the promise is done.  Initially {@code null} and lazily
     * instantiated when the late listener is scheduled to be notified later.  Also used as a cached {@link Runnable}
     * that performs the notification of the listeners it contains.
     */
    private LateListeners lateListeners;

    //等待的数量
    private short waiters;

    /**
     * Creates a new instance.
     *
     * It is preferable to use {@link EventExecutor#newPromise()} to create a new promise
     *
     * @param executor
     *        the {@link EventExecutor} which is used to notify the promise once it is complete
     */
    public DefaultPromise(EventExecutor executor) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
        this.executor = executor;
    }

    protected DefaultPromise() {
        // only for subclasses
        executor = null;
    }

    protected EventExecutor executor() {
        return executor;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled0(result);
    }

    private static boolean isCancelled0(Object result) {
        //判断是否已经取消
        return result instanceof CauseHolder && ((CauseHolder) result).cause instanceof CancellationException;
    }

    @Override
    public boolean isCancellable() {
        //是否可取消，在结果为null时，是可取消的
        return result == null;
    }

    @Override
    public boolean isDone() {
        return isDone0(result);
    }

    private static boolean isDone0(Object result) {
        //判断操作是否完成，结果未空且不是取消
        return result != null && result != UNCANCELLABLE;
    }

    @Override
    public boolean isSuccess() {
        Object result = this.result;
        if (result == null || result == UNCANCELLABLE) {
            return false;
        }
        //如果操作没有异常，就认为是成功状态
        return !(result instanceof CauseHolder);
    }

    @Override
    public Throwable cause() {
        Object result = this.result;
        if (result instanceof CauseHolder) {
            return ((CauseHolder) result).cause;
        }
        return null;
    }

    @Override
    public Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        //如果已经完成，则通知监听器，延迟
        if (isDone()) {
            notifyLateListener(listener);
            return this;
        }
        //同步，因为listners是个数组
        synchronized (this) {
            if (!isDone()) {
                if (listeners == null) {
                    listeners = listener;
                } else {
                    //DefaultFutureListeners实例
                    if (listeners instanceof DefaultFutureListeners) {
                        ((DefaultFutureListeners) listeners).add(listener);
                    } else {
                        //
                        final GenericFutureListener<? extends Future<V>> firstListener =
                                (GenericFutureListener<? extends Future<V>>) listeners;
                        listeners = new DefaultFutureListeners(firstListener, listener);
                    }
                }
                return this;
            }
        }
        //通知监听器
        notifyLateListener(listener);
        return this;
    }

    @Override
    public Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners");
        }

        for (GenericFutureListener<? extends Future<? super V>> l: listeners) {
            if (l == null) {
                break;
            }
            addListener(l);
        }
        return this;
    }

    @Override
    public Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        if (isDone()) {
            return this;
        }

        synchronized (this) {
            if (!isDone()) {
                if (listeners instanceof DefaultFutureListeners) {
                    ((DefaultFutureListeners) listeners).remove(listener);
                } else if (listeners == listener) {
                    listeners = null;
                }
            }
        }

        return this;
    }

    @Override
    public Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners");
        }

        for (GenericFutureListener<? extends Future<? super V>> l: listeners) {
            if (l == null) {
                break;
            }
            removeListener(l);
        }
        return this;
    }

    @Override
    public Promise<V> sync() throws InterruptedException {
        await();
        rethrowIfFailed();
        return this;
    }

    @Override
    public Promise<V> syncUninterruptibly() {
        awaitUninterruptibly();
        rethrowIfFailed();
        return this;
    }

    private void rethrowIfFailed() {
        Throwable cause = cause();
        if (cause == null) {
            return;
        }

        PlatformDependent.throwException(cause);
    }

    @Override
    public Promise<V> await() throws InterruptedException {
        //已经设置，返回promise对象
        if (isDone()) {
            return this;
        }
        //线程被中断，抛出中断异常
        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        //同步
        synchronized (this) {
            //循环直到结果被设置
            //循环判断是防止线程意外被唤醒导致功能异常
            while (!isDone()) {
                //死锁检查，自己等待自己的情况
                checkDeadLock();
                //增加等待线程
                incWaiters();
                try {
                    //Object wait
                    wait();
                } finally {
                    //减少等待线程
                    decWaiters();
                }
            }
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit)
            throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(MILLISECONDS.toNanos(timeoutMillis), true);
    }

    @Override
    public Promise<V> awaitUninterruptibly() {
        if (isDone()) {
            return this;
        }

        boolean interrupted = false;
        synchronized (this) {
            while (!isDone()) {
                checkDeadLock();
                incWaiters();
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Interrupted while waiting.
                    interrupted = true;
                } finally {
                    decWaiters();
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        return this;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            // Should not be raised at all.
            throw new InternalError();
        }
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            // Should not be raised at all.
            throw new InternalError();
        }
    }

    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        if (isDone()) {
            return true;
        }

        if (timeoutNanos <= 0) {
            return isDone();
        }

        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException(toString());
        }

        long startTime = System.nanoTime();
        long waitTime = timeoutNanos;
        boolean interrupted = false;

        try {
            synchronized (this) {
                if (isDone()) {
                    return true;
                }

                if (waitTime <= 0) {
                    return isDone();
                }

                checkDeadLock();
                incWaiters();
                try {
                    for (;;) {
                        try {
                            wait(waitTime / 1000000, (int) (waitTime % 1000000));
                        } catch (InterruptedException e) {
                            if (interruptable) {
                                throw e;
                            } else {
                                interrupted = true;
                            }
                        }

                        if (isDone()) {
                            return true;
                        } else {
                            waitTime = timeoutNanos - (System.nanoTime() - startTime);
                            if (waitTime <= 0) {
                                return isDone();
                            }
                        }
                    }
                } finally {
                    decWaiters();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Do deadlock checks
     */
    protected void checkDeadLock() {
        EventExecutor e = executor();
        if (e != null && e.inEventLoop()) {
            throw new BlockingOperationException(toString());
        }
    }

    @Override
    public Promise<V> setSuccess(V result) {
        //设置成功并通知
        if (setSuccess0(result)) {
            notifyListeners();
            return this;
        }
        throw new IllegalStateException("complete already: " + this);
    }

    @Override
    public boolean trySuccess(V result) {
        if (setSuccess0(result)) {
            notifyListeners();
            return true;
        }
        return false;
    }

    @Override
    public Promise<V> setFailure(Throwable cause) {
        if (setFailure0(cause)) {
            notifyListeners();
            return this;
        }
        throw new IllegalStateException("complete already: " + this, cause);
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        if (setFailure0(cause)) {
            notifyListeners();
            return true;
        }
        return false;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        Object result = this.result;
        if (isDone0(result) || result == UNCANCELLABLE) {
            return false;
        }

        synchronized (this) {
            // Allow only once.
            result = this.result;
            if (isDone0(result) || result == UNCANCELLABLE) {
                return false;
            }

            this.result = CANCELLATION_CAUSE_HOLDER;
            if (hasWaiters()) {
                notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    @Override
    public boolean setUncancellable() {
        Object result = this.result;
        if (isDone0(result)) {
            return !isCancelled0(result);
        }

        synchronized (this) {
            // Allow only once.
            result = this.result;
            if (isDone0(result)) {
                return !isCancelled0(result);
            }

            this.result = UNCANCELLABLE;
        }
        return true;
    }

    private boolean setFailure0(Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

        if (isDone()) {
            return false;
        }

        synchronized (this) {
            // Allow only once.
            if (isDone()) {
                return false;
            }

            result = new CauseHolder(cause);
            if (hasWaiters()) {
                notifyAll();
            }
        }
        return true;
    }

    private boolean setSuccess0(V result) {
        if (isDone()) {
            return false;
        }
        //可能存在用户线程和IO线程操作，加锁
        synchronized (this) {
            // Allow only once.
            //进行2次判断，提高性能
            if (isDone()) {
                return false;
            }
            //如果结果为null，设置为默认的SUCCESS
            //仅需要通知等待的线程
            if (result == null) {
                this.result = SUCCESS;
            } else {
                this.result = result;
            }
            //如果有等待的waiters即await产生，则全部通知
            if (hasWaiters()) {
                notifyAll();
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V getNow() {
        Object result = this.result;
        if (result instanceof CauseHolder || result == SUCCESS) {
            return null;
        }
        return (V) result;
    }

    private boolean hasWaiters() {
        return waiters > 0;
    }

    private void incWaiters() {
        if (waiters == Short.MAX_VALUE) {
            throw new IllegalStateException("too many waiters: " + this);
        }
        waiters ++;
    }

    private void decWaiters() {
        waiters --;
    }

    private void notifyListeners() {
        // This method doesn't need synchronization because:
        // 1) This method is always called after synchronized (this) block.
        //    Hence any listener list modification happens-before this method.
        // 2) This method is called only when 'done' is true.  Once 'done'
        //    becomes true, the listener list is never modified - see add/removeListener()

        Object listeners = this.listeners;
        if (listeners == null) {
            return;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();
            final int stackDepth = threadLocals.futureListenerStackDepth();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                threadLocals.setFutureListenerStackDepth(stackDepth + 1);
                try {
                    if (listeners instanceof DefaultFutureListeners) {
                        notifyListeners0(this, (DefaultFutureListeners) listeners);
                    } else {
                        final GenericFutureListener<? extends Future<V>> l =
                                (GenericFutureListener<? extends Future<V>>) listeners;
                        notifyListener0(this, l);
                    }
                } finally {
                    this.listeners = null;
                    threadLocals.setFutureListenerStackDepth(stackDepth);
                }
                return;
            }
        }

        if (listeners instanceof DefaultFutureListeners) {
            final DefaultFutureListeners dfl = (DefaultFutureListeners) listeners;
            execute(executor, new Runnable() {
                @Override
                public void run() {
                    notifyListeners0(DefaultPromise.this, dfl);
                    DefaultPromise.this.listeners = null;
                }
            });
        } else {
            final GenericFutureListener<? extends Future<V>> l =
                    (GenericFutureListener<? extends Future<V>>) listeners;
            execute(executor, new Runnable() {
                @Override
                public void run() {
                    notifyListener0(DefaultPromise.this, l);
                    DefaultPromise.this.listeners = null;
                }
            });
        }
    }

    private static void notifyListeners0(Future<?> future, DefaultFutureListeners listeners) {
        final GenericFutureListener<?>[] a = listeners.listeners();
        final int size = listeners.size();
        for (int i = 0; i < size; i ++) {
            notifyListener0(future, a[i]);
        }
    }

    /**
     * Notifies the specified listener which were added after this promise is already done.
     * This method ensures that the specified listener is not notified until {@link #listeners} becomes {@code null}
     * to avoid the case where the late listeners are notified even before the early listeners are notified.
     */
    private void notifyLateListener(final GenericFutureListener<?> l) {
        final EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            if (listeners == null && lateListeners == null) {
                final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();
                final int stackDepth = threadLocals.futureListenerStackDepth();
                if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                    threadLocals.setFutureListenerStackDepth(stackDepth + 1);
                    try {
                        notifyListener0(this, l);
                    } finally {
                        threadLocals.setFutureListenerStackDepth(stackDepth);
                    }
                    return;
                }
            } else {
                LateListeners lateListeners = this.lateListeners;
                if (lateListeners == null) {
                    this.lateListeners = lateListeners = new LateListeners();
                }
                lateListeners.add(l);
                execute(executor, lateListeners);
                return;
            }
        }

        // Add the late listener to lateListeners in the executor thread for thread safety.
        // We could just make LateListeners extend ConcurrentLinkedQueue, but it's an overkill considering
        // that most asynchronous applications won't execute this code path.
        execute(executor, new LateListenerNotifier(l));
    }

    protected static void notifyListener(
            final EventExecutor eventExecutor, final Future<?> future, final GenericFutureListener<?> l) {

        if (eventExecutor.inEventLoop()) {
            final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();
            final int stackDepth = threadLocals.futureListenerStackDepth();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                threadLocals.setFutureListenerStackDepth(stackDepth + 1);
                try {
                    notifyListener0(future, l);
                } finally {
                    threadLocals.setFutureListenerStackDepth(stackDepth);
                }
                return;
            }
        }

        execute(eventExecutor, new Runnable() {
            @Override
            public void run() {
                notifyListener0(future, l);
            }
        });
    }

    private static void execute(EventExecutor executor, Runnable task) {
        try {
            executor.execute(task);
        } catch (Throwable t) {
            rejectedExecutionLogger.error("Failed to submit a listener notification task. Event loop shut down?", t);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void notifyListener0(Future future, GenericFutureListener l) {
        try {
            //回调operationComplete方法。
            l.operationComplete(future);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("An exception was thrown by " + l.getClass().getName() + ".operationComplete()", t);
            }
        }
    }

    /**
     * Returns a {@link GenericProgressiveFutureListener}, an array of {@link GenericProgressiveFutureListener}, or
     * {@code null}.
     */
    private synchronized Object progressiveListeners() {
        Object listeners = this.listeners;
        if (listeners == null) {
            // No listeners added
            return null;
        }

        if (listeners instanceof DefaultFutureListeners) {
            // Copy DefaultFutureListeners into an array of listeners.
            DefaultFutureListeners dfl = (DefaultFutureListeners) listeners;
            int progressiveSize = dfl.progressiveSize();
            switch (progressiveSize) {
                case 0:
                    return null;
                case 1:
                    for (GenericFutureListener<?> l: dfl.listeners()) {
                        if (l instanceof GenericProgressiveFutureListener) {
                            return l;
                        }
                    }
                    return null;
            }

            GenericFutureListener<?>[] array = dfl.listeners();
            GenericProgressiveFutureListener<?>[] copy = new GenericProgressiveFutureListener[progressiveSize];
            for (int i = 0, j = 0; j < progressiveSize; i ++) {
                GenericFutureListener<?> l = array[i];
                if (l instanceof GenericProgressiveFutureListener) {
                    copy[j ++] = (GenericProgressiveFutureListener<?>) l;
                }
            }

            return copy;
        } else if (listeners instanceof GenericProgressiveFutureListener) {
            return listeners;
        } else {
            // Only one listener was added and it's not a progressive listener.
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    void notifyProgressiveListeners(final long progress, final long total) {
        final Object listeners = progressiveListeners();
        if (listeners == null) {
            return;
        }

        final ProgressiveFuture<V> self = (ProgressiveFuture<V>) this;

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            if (listeners instanceof GenericProgressiveFutureListener[]) {
                notifyProgressiveListeners0(
                        self, (GenericProgressiveFutureListener<?>[]) listeners, progress, total);
            } else {
                notifyProgressiveListener0(
                        self, (GenericProgressiveFutureListener<ProgressiveFuture<V>>) listeners, progress, total);
            }
        } else {
            if (listeners instanceof GenericProgressiveFutureListener[]) {
                final GenericProgressiveFutureListener<?>[] array =
                        (GenericProgressiveFutureListener<?>[]) listeners;
                execute(executor, new Runnable() {
                    @Override
                    public void run() {
                        notifyProgressiveListeners0(self, array, progress, total);
                    }
                });
            } else {
                final GenericProgressiveFutureListener<ProgressiveFuture<V>> l =
                        (GenericProgressiveFutureListener<ProgressiveFuture<V>>) listeners;
                execute(executor, new Runnable() {
                    @Override
                    public void run() {
                        notifyProgressiveListener0(self, l, progress, total);
                    }
                });
            }
        }
    }

    private static void notifyProgressiveListeners0(
            ProgressiveFuture<?> future, GenericProgressiveFutureListener<?>[] listeners, long progress, long total) {
        for (GenericProgressiveFutureListener<?> l: listeners) {
            if (l == null) {
                break;
            }
            notifyProgressiveListener0(future, l, progress, total);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void notifyProgressiveListener0(
            ProgressiveFuture future, GenericProgressiveFutureListener l, long progress, long total) {
        try {
            l.operationProgressed(future, progress, total);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("An exception was thrown by " + l.getClass().getName() + ".operationProgressed()", t);
            }
        }
    }

    private static final class CauseHolder {
        final Throwable cause;
        CauseHolder(Throwable cause) {
            this.cause = cause;
        }
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    protected StringBuilder toStringBuilder() {
        StringBuilder buf = new StringBuilder(64)
            .append(StringUtil.simpleClassName(this))
            .append('@')
            .append(Integer.toHexString(hashCode()));

        Object result = this.result;
        if (result == SUCCESS) {
            buf.append("(success)");
        } else if (result == UNCANCELLABLE) {
            buf.append("(uncancellable)");
        } else if (result instanceof CauseHolder) {
            buf.append("(failure(")
               .append(((CauseHolder) result).cause)
               .append(')');
        } else {
            buf.append("(incomplete)");
        }
        return buf;
    }

    private final class LateListeners extends ArrayDeque<GenericFutureListener<?>> implements Runnable {

        private static final long serialVersionUID = -687137418080392244L;

        LateListeners() {
            super(2);
        }

        @Override
        public void run() {
            if (listeners == null) {
                for (;;) {
                    GenericFutureListener<?> l = poll();
                    if (l == null) {
                        break;
                    }
                    notifyListener0(DefaultPromise.this, l);
                }
            } else {
                // Reschedule until the initial notification is done to avoid the race condition
                // where the notification is made in an incorrect order.
                execute(executor(), this);
            }
        }
    }

    private final class LateListenerNotifier implements Runnable {
        private GenericFutureListener<?> l;

        LateListenerNotifier(GenericFutureListener<?> l) {
            this.l = l;
        }

        @Override
        public void run() {
            LateListeners lateListeners = DefaultPromise.this.lateListeners;
            if (l != null) {
                if (lateListeners == null) {
                    DefaultPromise.this.lateListeners = lateListeners = new LateListeners();
                }
                lateListeners.add(l);
                l = null;
            }

            lateListeners.run();
        }
    }
}
