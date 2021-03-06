/**
 * 
 */
package basic;

import io.netty.bootstrap.Bootstrap;
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
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * ��/�ӿ�ע��
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-18
 * 
 */
public class TimeClient {

    public static void main(String[] args) throws Exception{
        String host = "127.0.0.1";
        int port = 8080;
        new TimeClient().connect(host, port);
    }
    
    public void connect(String host,int port) throws Exception{
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        try{
            b.group(group).channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(new ChannelInitializer<SocketChannel>(){
              @Override
              protected void initChannel(SocketChannel channel) throws Exception {
                  channel.pipeline().addLast(new TimeClientHandler());
              }
            });
          ChannelFuture f = b.connect(host, port).sync();
          f.channel().closeFuture().sync();
        }finally{
            group.shutdownGracefully();
        }
        
    }
    
    private static class TimeClientHandler extends ChannelHandlerAdapter{

        private ByteBuf message;
        
        public TimeClientHandler(){
            byte[] req = "QUERY TIME ORDER".getBytes();
            message = Unpooled.buffer(req.length);
            message.writeBytes(req);
        }
       
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
           ctx.writeAndFlush(message);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf)msg;
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            String body = new String(bytes,"UTF-8");
            System.out.println("Now is :" + body);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.out.println("Unexpect exceptoin from downstream:" + cause.getMessage());
            ctx.close();
        }
        
    }
}
