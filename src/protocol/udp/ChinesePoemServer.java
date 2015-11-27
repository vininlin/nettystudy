/**
 * 
 */
package protocol.udp;

import java.util.concurrent.ThreadLocalRandom;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public class ChinesePoemServer {
    
    public static void main(String[] args){
        int port = 8080;
        new ChinesePoemServer().run(port);
    }

    public void run(int port){
        EventLoopGroup group = new NioEventLoopGroup();
        try{
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioDatagramChannel.class)
              .option(ChannelOption.SO_BROADCAST, true)
              .handler(new ChinesePoemServerHandler());
            b.bind(port).sync().channel().closeFuture().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally{
            group.shutdownGracefully();
        }
    }
    
    static class ChinesePoemServerHandler extends SimpleChannelInboundHandler<DatagramPacket>{

        private static final String[] DICT = new String[]{"旧时王谢堂前燕，飞入寻常百姓家。",
            "洛阳亲友如相问，一片冰心在玉壶。","沧海月明珠有泪，蓝田日暖玉生烟。",
            "念去去，千里烟波，暮霭沉沉楚天阔。"};
        @Override
        protected void messageReceived(ChannelHandlerContext ctx, DatagramPacket packet)
                throws Exception {
            
            String request = packet.content().toString(CharsetUtil.UTF_8);
            System.out.println("receive udp packet.." + request);
            if("诗词查询？".equals(request)){
                System.out.println("send result..");
                ctx.writeAndFlush(new DatagramPacket(
                        Unpooled.copiedBuffer("查询结果：" + nextQuote(),CharsetUtil.UTF_8),
                        packet.sender()));
            }
        }
        
        private String nextQuote(){
            int quoteId = ThreadLocalRandom.current().nextInt(DICT.length);
            return DICT[quoteId];
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
        
    }
}
