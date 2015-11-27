/**
 * 
 */
package tcpdecoder;

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
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-19
 * 
 */
public class DelimiterEchoClient {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 8080;
        new DelimiterEchoClient().connect(host, port);
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
                  ByteBuf delimiter = Unpooled.copiedBuffer("$_".getBytes());
                  channel.pipeline().addLast(new DelimiterBasedFrameDecoder(1024,delimiter));
                  channel.pipeline().addLast(new StringDecoder());
                  channel.pipeline().addLast(new EchoClientHandler());
              }
            });
          ChannelFuture f = b.connect(host, port).sync();
          f.channel().closeFuture().sync();
        }finally{
            group.shutdownGracefully();
        }
        
    }
    
    private static class EchoClientHandler extends ChannelHandlerAdapter{

        private int counter;
        
        static final String ECHO_REQ = "Hi,Welcome to netty.$_";
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            for(int i = 0 ; i < 10; i++){
                ctx.writeAndFlush(Unpooled.copiedBuffer(ECHO_REQ.getBytes()));
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("This is " + ++counter + " times receive server : [" + msg + "]");
        }

      
        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
        
    }
}
