/**
 * 
 */
package encodecode.marshalling;

import io.netty.bootstrap.ServerBootstrap;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-19
 * 
 */
public class SubRequestServer {

    
    public static void main(String[] args) {
        int port = 8080;
        try {
            new SubRequestServer().bind(port);
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
                        channel.pipeline().addLast(MarshallingCodeCFactory.buildMarshallingDecoder());
                        channel.pipeline().addLast(MarshallingCodeCFactory.buildMarshallingEncoder());
                        channel.pipeline().addLast(new SubReqServerHandler());
                    }
              });
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        }finally{
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
        
    }
    
    private static class SubReqServerHandler extends ChannelHandlerAdapter{

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
           SubscribeReq req = (SubscribeReq)msg;
           if("vinin".equals(req.getUserName())){
               System.out.println("Server accept client subscribe req:[ " + req.toString() + "]");
               ctx.writeAndFlush(resp(req));
           }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
        
        private SubscribeResp resp(SubscribeReq req){
            SubscribeResp resp = new SubscribeResp();
            resp.setSubReqID(req.getSubReqId());
            resp.setRespCode(0);
            resp.setDesc("Netty book....");
            return resp;
        }
        
    }
    
}
