/**
 * 
 */
package tcpdecoder;

import io.netty.bootstrap.ServerBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-19
 * 
 */
public class DelimiterEchoServer {
    
    public static void main(String[] args) {
        int port = 8080;
        try {
            new DelimiterEchoServer().bind(port);
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
                    protected void initChannel(SocketChannel channel) throws Exception {
                        ByteBuf delimiter = Unpooled.copiedBuffer("$_".getBytes());
                        channel.pipeline().addLast(new DelimiterBasedFrameDecoder(1024,delimiter));
                        channel.pipeline().addLast(new StringDecoder());
                        channel.pipeline().addLast(new EchoServerHandler());
                    }
              });
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        }finally{
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
        
    }
    
    private static class EchoServerHandler extends ChannelHandlerAdapter{
        
        private int counter;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
           String body = (String)msg;
           System.out.println("This is " + ++counter + " times receive from client [" + body + "]");
           body += "$_";
           ByteBuf buf = Unpooled.copiedBuffer(body.getBytes());
           ctx.writeAndFlush(buf);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
        
    }
    
}
