/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FileRegion;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on bytes.
 */
public abstract class AbstractNioByteChannel extends AbstractNioChannel {

    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(FileRegion.class) + ')';

    private Runnable flushTask;

    /**
     * Create a new instance
     *
     * @param parent            the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param ch                the underlying {@link SelectableChannel} on which it operates
     */
    protected AbstractNioByteChannel(Channel parent, SelectableChannel ch) {
        super(parent, ch, SelectionKey.OP_READ);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioByteUnsafe();
    }

    protected class NioByteUnsafe extends AbstractNioUnsafe {

        private void closeOnRead(ChannelPipeline pipeline) {
            SelectionKey key = selectionKey();
            setInputShutdown();
            if (isOpen()) {
                if (Boolean.TRUE.equals(config().getOption(ChannelOption.ALLOW_HALF_CLOSURE))) {
                    key.interestOps(key.interestOps() & ~readInterestOp);
                    pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                } else {
                    close(voidPromise());
                }
            }
        }

        private void handleReadException(ChannelPipeline pipeline,
                                         ByteBuf byteBuf, Throwable cause, boolean close) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    setReadPending(false);
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    byteBuf.release();
                }
            }
            pipeline.fireChannelReadComplete();
            pipeline.fireExceptionCaught(cause);
            if (close || cause instanceof IOException) {
                closeOnRead(pipeline);
            }
        }

        @Override
        public final void read() {
            //获取Channel配置
            final ChannelConfig config = config();
            //如果非自动读则删除OP_READ标识位
            if (!config.isAutoRead() && !isReadPending()) {
                // ChannelConfig.setAutoRead(false) was called in the meantime
                removeReadOp();
                return;
            }
            //获取ChannelPipeline、
            final ChannelPipeline pipeline = pipeline();
            //ByteBufAllocator,默认的是ByteBufUtil.DEFAULT_ALLOCATOR,
            //即 PooledByteBufAllocator.DEFAULT
            final ByteBufAllocator allocator = config.getAllocator();
            //获取配置中每次最大的读取消息长度
            final int maxMessagesPerRead = config.getMaxMessagesPerRead();
            //AdaptiveRecvByteBufAllocator.newHandle();
            //缓冲区大小动态调整
            RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();

            ByteBuf byteBuf = null;
            int messages = 0;
            boolean close = false;
            try {
                int totalReadAmount = 0;
                boolean readPendingReset = false;
                //当读取的消息小于取配置中每次最大的读取消息长度时，循环。
                do {
                    //动态分配缓冲区
                    byteBuf = allocHandle.allocate(allocator);
                    //获取缓冲区可写字节数
                    int writable = byteBuf.writableBytes();
                    //调用外部类的doReadBytes读入缓冲区
                    int localReadAmount = doReadBytes(byteBuf);
                    //没有可读字节，释放
                    if (localReadAmount <= 0) {
                        // not was read release the buffer
                        byteBuf.release();
                        byteBuf = null;
                        close = localReadAmount < 0;
                        break;
                    }
                    if (!readPendingReset) {
                        readPendingReset = true;
                        setReadPending(false);
                    }
                    //ChannelPipeline触发ChannelRead事件
                    pipeline.fireChannelRead(byteBuf);
                    byteBuf = null;

                    if (totalReadAmount >= Integer.MAX_VALUE - localReadAmount) {
                        // Avoid overflow.
                        totalReadAmount = Integer.MAX_VALUE;
                        break;
                    }

                    totalReadAmount += localReadAmount;

                    // stop reading
                    if (!config.isAutoRead()) {
                        break;
                    }

                    if (localReadAmount < writable) {
                        // Read less than what the buffer can hold,
                        // which might mean we drained the recv buffer completely.
                        break;
                    }
                } while (++ messages < maxMessagesPerRead);
              //ChannelPipeline触发ChannelReadComplete事件
                pipeline.fireChannelReadComplete();
                //动态缓冲区分配器记录本次读取的总字节数
                //目的是为了下一次读取动态调整
                allocHandle.record(totalReadAmount);

                if (close) {
                    closeOnRead(pipeline);
                    close = false;
                }
            } catch (Throwable t) {
                handleReadException(pipeline, byteBuf, t, close);
            } finally {
                // Check if there is a readPending which was not processed yet.
                // This could be for two reasons:
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
                //
                // See https://github.com/netty/netty/issues/2254
                if (!config.isAutoRead() && !isReadPending()) {
                    removeReadOp();
                }
            }
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        //自旋次数设置为-1
        int writeSpinCount = -1;
        //自旋
        for (;;) {
            Object msg = in.current();
            //说明已经写完，清理op标志位，跳出循环
            if (msg == null) {
                // Wrote all messages.
                clearOpWrite();
                break;
            }
            //如果是ByteBuf实例
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                int readableBytes = buf.readableBytes();
                //可读字节为0，删除该消息，继续循环
                if (readableBytes == 0) {
                    in.remove();
                    continue;
                }
                //
                boolean setOpWrite = false;
                boolean done = false;
                long flushedAmount = 0;
                //从配置中读取自旋次数
                if (writeSpinCount == -1) {
                    writeSpinCount = config().getWriteSpinCount();
                }
                //根据writeSpinCount循环写，
                //设置writeSpinCount的原因是为了I/O线程不会一直被写操作占用
                for (int i = writeSpinCount - 1; i >= 0; i --) {
                    //doWriteBytes由子类实现
                    int localFlushedAmount = doWriteBytes(buf);
                    //如果localFlushedAmount为0，说明写完，跳出。
                    if (localFlushedAmount == 0) {
                        setOpWrite = true;
                        break;
                    }

                    flushedAmount += localFlushedAmount;
                    //buf已经没有可读字节，完成，跳出
                    if (!buf.isReadable()) {
                        done = true;
                        break;
                    }
                }
                //设置写的进度
                in.progress(flushedAmount);
                //完成则删除该消息，否则设置未写完标志位,异步线程继续写。。
                if (done) {
                    in.remove();
                } else {
                    incompleteWrite(setOpWrite);
                    break;
                }
            } else if (msg instanceof FileRegion) {
                //如果是文件块实现
                FileRegion region = (FileRegion) msg;
                boolean done = region.transfered() >= region.count();
                boolean setOpWrite = false;
                //没处理完，实现与ByteBuf实例基本一致。
                if (!done) {
                    long flushedAmount = 0;
                    if (writeSpinCount == -1) {
                        writeSpinCount = config().getWriteSpinCount();
                    }

                    for (int i = writeSpinCount - 1; i >= 0; i--) {
                        long localFlushedAmount = doWriteFileRegion(region);
                        if (localFlushedAmount == 0) {
                            setOpWrite = true;
                            break;
                        }

                        flushedAmount += localFlushedAmount;
                        if (region.transfered() >= region.count()) {
                            done = true;
                            break;
                        }
                    }

                    in.progress(flushedAmount);
                }

                if (done) {
                    in.remove();
                } else {
                    incompleteWrite(setOpWrite);
                    break;
                }
            } else {
                // Should not reach here.
                throw new Error();
            }
        }
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (buf.isDirect()) {
                return msg;
            }

            return newDirectBuffer(buf);
        }

        if (msg instanceof FileRegion) {
            return msg;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    protected final void incompleteWrite(boolean setOpWrite) {
        // Did not write completely.
        if (setOpWrite) {
            setOpWrite();
        } else {
            // Schedule flush again later so other tasks can be picked up in the meantime
            Runnable flushTask = this.flushTask;
            if (flushTask == null) {
                flushTask = this.flushTask = new Runnable() {
                    @Override
                    public void run() {
                        flush();
                    }
                };
            }
            eventLoop().execute(flushTask);
        }
    }

    /**
     * Write a {@link FileRegion}
     *
     * @param region        the {@link FileRegion} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    protected abstract long doWriteFileRegion(FileRegion region) throws Exception;

    /**
     * Read bytes into the given {@link ByteBuf} and return the amount.
     */
    protected abstract int doReadBytes(ByteBuf buf) throws Exception;

    /**
     * Write bytes form the given {@link ByteBuf} to the underlying {@link java.nio.channels.Channel}.
     * @param buf           the {@link ByteBuf} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    protected abstract int doWriteBytes(ByteBuf buf) throws Exception;

    protected final void setOpWrite() {
        final SelectionKey key = selectionKey();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(interestOps | SelectionKey.OP_WRITE);
        }
    }

    protected final void clearOpWrite() {
        final SelectionKey key = selectionKey();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        }
    }
}
