/**
 * 
 */
package protocol.priv;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public class NettyServer {

    public static void main(String[] args) {
        int port = 8080;
        try {
            new NettyServer().bind(port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void bind(int port) throws Exception{
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
              .channel(NioServerSocketChannel.class)
              .option(ChannelOption.SO_BACKLOG, 1024)
              .childHandler(new ChannelInitializer<SocketChannel>(){
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new NettyMessageDecoder(1024*1024,4,4,-8,0))
                    .addLast(new NettyMessageEncoder())
                    .addLast(new ReadTimeoutHandler(50))
                    .addLast(new LoginAuthRespHandler())
                    .addLast(new HeartBeatRespHandler());
                }
              });
            ChannelFuture f = b.bind(port).sync();
            System.out.println("Netty Server start at port :" + port);
            f.channel().closeFuture().sync();
        }finally{
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
