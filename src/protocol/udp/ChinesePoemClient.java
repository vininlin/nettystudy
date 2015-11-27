/**
 * 
 */
package protocol.udp;

import java.net.InetSocketAddress;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import protocol.udp.ChinesePoemServer.ChinesePoemServerHandler;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public class ChinesePoemClient {

   
    public static void main(String[] args) {
        int port = 8080;
        new ChinesePoemClient().run(port);
    }
    
    public void run(int port){
        EventLoopGroup group = new NioEventLoopGroup();
        try{
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioDatagramChannel.class)
              .option(ChannelOption.SO_BROADCAST, true)
              .handler(new ChinesePoemClientHandler());
            Channel ch = b.bind(0).sync().channel();
            ch.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer("诗词查询？" ,CharsetUtil.UTF_8),
                                        new InetSocketAddress("255.255.255.255",port))).sync();
            if(!ch.closeFuture().await(5000)){
                System.out.println("查询超时");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally{
            group.shutdownGracefully();
        }
    }

    static class ChinesePoemClientHandler extends SimpleChannelInboundHandler<DatagramPacket>{

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, DatagramPacket packet)
                throws Exception {
            String response = packet.content().toString(CharsetUtil.UTF_8);
            if(response.startsWith("查询结果：")){
                System.out.println(response);
                ctx.close();
            }
        }
      
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
        
    }
}
