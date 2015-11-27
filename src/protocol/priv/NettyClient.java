/**
 * 
 */
package protocol.priv;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public class NettyClient {
    
    private static final String REMOTE_IP = "127.0.0.1";
    private static final int REMOTE_PORT = 8080;
    
    public static void main(String[] args) {
       
        try {
            new NettyClient().connect(REMOTE_IP, REMOTE_PORT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    
    private EventLoopGroup group = new NioEventLoopGroup();
    
    public void connect(String host,int port) throws Exception{
        try{
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>(){
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        //-8表示lengthAdjustment,让解码器从0开始截取字节，并包含消息头
                        ch.pipeline().addLast(new NettyMessageDecoder(1024*1024,4,4,-8,0))
                                          .addLast(new NettyMessageEncoder())
                                          .addLast(new ReadTimeoutHandler(50))
                                          .addLast(new LoginAuthReqHandler())
                                          .addLast(new HeartBeatReqHandler());
                    }
                });
            ChannelFuture f = b.connect(new InetSocketAddress(host,port), new InetSocketAddress("127.0.0.1",8081)).sync();
            System.out.println("Netty client connect " + host + ":" + port);
            f.channel().closeFuture().sync();
        }finally{
            
            executor.execute(new Runnable(){
                @Override
                public void run() {
                    try{
                        TimeUnit.SECONDS.sleep(4);
                        System.out.println("do reconnect...");
                        connect(REMOTE_IP,REMOTE_PORT);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
            });
        }
    }

}
