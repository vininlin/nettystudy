/**
 * 
 */
package nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-17
 * 
 */
public class NIOTimeClient {

    public static void main(String[] args){
        String host = "127.0.0.1";
        int port = 8080;
        new Thread(new TimeClientHandler(host,port)).start();
    }
    
    static class TimeClientHandler implements Runnable{
        
        private String host;
        private int port;
        private Selector selector;
        private SocketChannel channel;
        private volatile boolean stop;
        
        public TimeClientHandler(String host,int port){
            this.host = host;
            this.port = port;
            try {
                selector = Selector.open();
                channel = SocketChannel.open();
                channel.configureBlocking(false);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        
        @Override
        public void run() {
            try {
                doConnect();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
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
                SocketChannel sc = (SocketChannel)key.channel();
                if(key.isConnectable()){
                    if(sc.finishConnect()){
                        sc.register(selector, SelectionKey.OP_READ);
                        doWrite(sc);
                    }else{
                        System.exit(1);
                    }
                }
                if(key.isReadable()){
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    int count = sc.read(buffer);
                    if(count > 0){
                        buffer.flip();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        String body = new String(bytes,"UTF-8");
                        System.out.println("Now is :" + body);
                        this.stop = true;
                    }else if(count < 0){
                        key.cancel();
                        sc.close();
                    }else{
                        //
                    }
                }
            }
        }
        
        private void doConnect() throws IOException{
            if(channel.connect(new InetSocketAddress(host,port))){
                channel.register(selector, SelectionKey.OP_READ);
                doWrite(channel);
            }else{
                channel.register(selector, SelectionKey.OP_CONNECT);
            }
        }
        
        private void doWrite(SocketChannel channel) throws IOException{
            byte[] req = "QUERY TIME ORDER".getBytes();
            ByteBuffer buffer = ByteBuffer.allocate(req.length);
            buffer.put(req);
            buffer.flip();
            channel.write(buffer);
            if(!buffer.hasRemaining()){
                System.out.println("Send order to server succeed");
            }
        }
        
    }
    
}
