/**
 * 
 */
package encodecode.marshalling;

import io.netty.bootstrap.Bootstrap;

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
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-19
 * 
 */
public class SubRequestClient {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 8080;
        new SubRequestClient().connect(host, port);
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
                  channel.pipeline().addLast(MarshallingCodeCFactory.buildMarshallingDecoder());
                  channel.pipeline().addLast(MarshallingCodeCFactory.buildMarshallingEncoder());
                  channel.pipeline().addLast(new SubRequestClientHandler());
              }
            });
          ChannelFuture f = b.connect(host, port).sync();
          f.channel().closeFuture().sync();
        }finally{
            group.shutdownGracefully();
        }
        
    }
    
    private static class SubRequestClientHandler extends ChannelHandlerAdapter{

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            for(int i = 0 ; i < 10; i++){
                ctx.writeAndFlush(subReq(i));
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("Receive server resp: [" + msg + "]");
        }

        private SubscribeReq subReq(int i ){
            SubscribeReq req = new SubscribeReq();
            req.setSubReqId(i);
            req.setUserName("vinin");
            req.setPhoneNumber("13800138000");
            req.setProductName("netty");
            req.setAddress("广州天河");
            return req;
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
