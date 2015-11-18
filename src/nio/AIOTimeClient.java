/**
 * 
 */
package nio;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-17
 * 
 */
public class AIOTimeClient {

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 8080;
        AsynTimeClientHandler clientHandler = new AsynTimeClientHandler(host,port);
        new Thread(clientHandler,"AsynTimeClientHandler-001").start();
    }
    
    static class AsynTimeClientHandler implements CompletionHandler<Void,AsynTimeClientHandler>,Runnable{
      
        private AsynchronousSocketChannel client;
        private String host;
        private int port;
        private CountDownLatch latch;
        
        public AsynTimeClientHandler(String host,int port){
            this.host = host;
            this.port = port;
            try {
                client = AsynchronousSocketChannel.open();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        @Override
        public void run() {
            latch = new CountDownLatch(1);
            client.connect(new InetSocketAddress(host,port), this, this);
            try {
                System.out.println("wait for reponse");
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        @Override
        public void completed(Void result, AsynTimeClientHandler attachment) {
            System.out.println("connect succeed..");
            byte[] req = "QUERY TIME ORDER".getBytes();
            ByteBuffer buffer = ByteBuffer.allocate(req.length);
            buffer.put(req);
            buffer.flip();
            client.write(buffer,buffer,new CompletionHandler<Integer,ByteBuffer>(){

                @Override
                public void completed(Integer result, ByteBuffer buffer) {
                    System.out.println("Send order to server succeed");
                    if(buffer.hasRemaining()){
                        System.out.println("write hasRemaining..");
                        client.write(buffer,buffer,this);
                        if(!buffer.hasRemaining()){
                            System.out.println("Send order to server succeed");
                        }
                    }else{
                        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                        System.out.println("read from server..");
                        client.read(readBuffer,readBuffer,new CompletionHandler<Integer,ByteBuffer>(){

                            @Override
                            public void completed(Integer result, ByteBuffer buffer) {
                                System.out.println("read from server completed..");
                                buffer.flip();
                                byte[] bytes = new byte[buffer.remaining()];
                                buffer.get(bytes);
                                String body;
                                try {
                                    body = new String(bytes,"UTF-8");
                                    System.out.println("Now is :" + body);
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void failed(Throwable exc, ByteBuffer buffer) {
                                exc.printStackTrace();
                                try {
                                    client.close();
                                    latch.countDown();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            
                        });
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer buffer) {
                    exc.printStackTrace();
                    try {
                        client.close();
                        latch.countDown();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                
            });
        }
       
        @Override
        public void failed(Throwable exc, AsynTimeClientHandler attachment) {
            exc.printStackTrace();
            try {
                client.close();
                latch.countDown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
