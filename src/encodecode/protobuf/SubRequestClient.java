/**
 * 
 */
package encodecode.protobuf;

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
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.codec.string.StringDecoder;

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
                  channel.pipeline().addLast(new ProtobufVarint32FrameDecoder());
                  channel.pipeline().addLast(new ProtobufDecoder(SubscribeRespProto.SubscribeResp.getDefaultInstance()));
                  channel.pipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
                  channel.pipeline().addLast(new ProtobufEncoder());
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
                ctx.write(subReq(i));
            }
            ctx.flush();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("Receive server resp: [" + msg + "]");
        }

        private SubscribeReqProto.SubscribeReq subReq(int i ){
            SubscribeReqProto.SubscribeReq.Builder builder = SubscribeReqProto.SubscribeReq.newBuilder();
            builder.setSubReqID(i);
            builder.setUserName("vinin");
            builder.setProductName("netty");
            builder.setAddress("广州天河");
            return builder.build();
        }
      
        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.out.println(cause.getMessage());
            cause.printStackTrace();
            ctx.close();
        }
        
    }
}
