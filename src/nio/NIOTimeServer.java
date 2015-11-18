/**
 * 
 */
package nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-17
 * 
 */
public class NIOTimeServer {

    public static void main(String[] args){
        int port = 8080;
        MultiplexerTimeServer server = new MultiplexerTimeServer(port);
        new Thread(server).start();
    }
    
    static class MultiplexerTimeServer implements Runnable{
        
        private Selector selector;
        private ServerSocketChannel serverChannel ;
        private volatile boolean stop;
        
        public MultiplexerTimeServer(int port){
            try {
                selector = Selector.open();
                serverChannel = ServerSocketChannel.open();
                serverChannel.configureBlocking(false);
                serverChannel.socket().bind(new InetSocketAddress(port));
                serverChannel.register(selector, SelectionKey.OP_ACCEPT);
                System.out.println("TimeServer start on " + port);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        
        public void stop(){
            this.stop = true;
        }
        
        @Override
        public void run() {
            while(!stop){
                try {
                    selector.select(1000);
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> it = keys.iterator();
                    SelectionKey key = null;
                    while(it.hasNext()){
                        key = it.next();
                        it.remove();
                        try{
                            handleInput(key);
                        }catch(IOException e1){
                            if(key != null){
                                key.cancel();
                                if(key.channel() != null)
                                    key.channel().close();
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(selector != null){
                try {
                    selector.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        private void handleInput(SelectionKey key) throws IOException{
            if(key.isValid()){
                if(key.isAcceptable()){
                    ServerSocketChannel ssc = (ServerSocketChannel)key.channel();
                    SocketChannel sc = ssc.accept();
                    sc.configureBlocking(false);
                    sc.register(selector, SelectionKey.OP_READ);
                }
                if(key.isReadable()){
                    SocketChannel sc = (SocketChannel)key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    int count = sc.read(buffer);
                    if(count > 0){
                        buffer.flip();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        String body = new String(bytes,"UTF-8");
                        System.out.println("TimeServer receive order : " + body);
                        String currentTime = "QUERY TIME ORDER".equalsIgnoreCase(body) ?
                                new Date(System.currentTimeMillis()).toString() : "BAD ORDER";
                        doWrite(sc,currentTime);
                    }else if(count < 0){
                        key.cancel();
                        sc.close();
                    }else{
                        //
                    }
                }
            }
        }
        
        private void doWrite(SocketChannel sc,String response ) throws IOException{
            if(response != null && response.trim().length() > 0){
                byte[] bytes = response.getBytes();
                ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
                buffer.put(bytes);
                buffer.flip();
                sc.write(buffer);
            }
        }
        
    }
}
