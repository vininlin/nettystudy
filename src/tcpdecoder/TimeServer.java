/**
 * 
 */
package tcpdecoder;


import java.util.Date;

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
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-18
 * 
 */
public class TimeServer {
    
    public static void main(String[] args) throws Exception{
        int port = 8080;
        System.out.println("TimeServer start on " + port);
        new TimeServer().bind(port);
    }

    public void bind(int port) throws Exception{
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
              .channel(NioServerSocketChannel.class)
              .option(ChannelOption.SO_BACKLOG, 1024)
              .childHandler(new ChildChannelHandler());
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        }finally{
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
        
    }
    
    private static class ChildChannelHandler extends ChannelInitializer<SocketChannel>{

        @Override
        protected void initChannel(SocketChannel channel) throws Exception {
            //channel.pipeline().addLast(new LineBasedFrameDecoder(1024));
            //channel.pipeline().addLast(new StringDecoder());
            channel.pipeline().addLast(new TimeServerHandler());
        }
        
    }
    
    private static class TimeServerHandler extends ChannelHandlerAdapter{
        
        private int counter;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf)msg;
            byte[] req = new byte[buf.readableBytes()];
            buf.readBytes(req);
            String body = new String(req,"UTF-8").substring(0,
                    req.length - System.getProperty("line.separator").length());
            //String body = (String)msg;
            System.out.println("TimeServer receive order : " + body + ";the counter is " + (++counter));
            String currentTime = "QUERY TIME ORDER".equalsIgnoreCase(body) ?
                    new Date(System.currentTimeMillis()).toString() : "BAD ORDER";
            currentTime = currentTime + System.getProperty("line.separator");
            ByteBuf response = Unpooled.copiedBuffer(currentTime.getBytes());
            ctx.write(response);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
           ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.close();
        }
        
    }
}
