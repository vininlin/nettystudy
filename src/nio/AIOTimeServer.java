/**
 * 
 */
package nio;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-17
 * 
 */
public class AIOTimeServer {

    /**
     * @param args
     */
    public static void main(String[] args) {
        int port = 8080;
        AsynTimeServerHandler syncServer = new AsynTimeServerHandler(port);
        new Thread(syncServer,"AIO-TimeServerHandler-001").start();
    }
    
    static class AsynTimeServerHandler implements Runnable{

        CountDownLatch latch;
        AsynchronousServerSocketChannel asynServerSocketChannel ;
        
        public AsynTimeServerHandler(int port){
            try {
                asynServerSocketChannel = AsynchronousServerSocketChannel.open();
                asynServerSocketChannel.bind(new InetSocketAddress(port));
                System.out.println("TimeServer start on " + port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        @Override
        public void run() {
            latch = new CountDownLatch(1);
            doAccept();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        private void doAccept(){
            asynServerSocketChannel.accept(this,new AcceptCompletionHandler());
        }
    }
    
    static class AcceptCompletionHandler 
                        implements CompletionHandler<AsynchronousSocketChannel,AsynTimeServerHandler>{

        @Override
        public void completed(AsynchronousSocketChannel result, AsynTimeServerHandler attachment) {
            attachment.asynServerSocketChannel.accept(attachment,this);
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            result.read(buffer, buffer, new ReadCompletionHandler(result) );
        }

        @Override
        public void failed(Throwable exc, AsynTimeServerHandler attachment) {
            exc.printStackTrace();
            attachment.latch.countDown();
        }
    }
    
    static class ReadCompletionHandler implements CompletionHandler<Integer,ByteBuffer>{
        
        AsynchronousSocketChannel channel;
        
        public ReadCompletionHandler(AsynchronousSocketChannel channel){
            if(this.channel == null){
                this.channel = channel;
            }
        }

        @Override
        public void completed(Integer result, ByteBuffer buffer) {
            buffer.flip();
            byte[] body = new byte[buffer.remaining()];
            buffer.get(body);
            String req;
            try {
                req = new String(body,"UTF-8");
                System.out.println("TimeServer receive order : " + req);
                String currentTime = "QUERY TIME ORDER".equalsIgnoreCase(req) ?
                        new Date(System.currentTimeMillis()).toString() : "BAD ORDER";
                doWrite(currentTime);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            try {
                this.channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        private void doWrite(String response ) throws IOException{
            if(response != null && response.trim().length() > 0){
                byte[] bytes = response.getBytes();
                ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
                buffer.put(bytes);
                buffer.flip();
                System.out.println("channel:"+channel);
                channel.write(buffer, buffer, new CompletionHandler<Integer,ByteBuffer>(){

                    @Override
                    public void completed(Integer result, ByteBuffer attachment) {
                        if(attachment.hasRemaining()){
                            channel.write(attachment, attachment, this);
                        }
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        try {
                            channel.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }
    }

}
