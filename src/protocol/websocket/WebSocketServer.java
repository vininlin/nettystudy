/**
 * 
 */
package protocol.websocket;

import java.util.Date;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;

import static  io.netty.handler.codec.http.HttpHeaderNames.*;
import static  io.netty.handler.codec.http.HttpVersion.*;
import static  io.netty.handler.codec.http.HttpResponseStatus.*;
import static  io.netty.handler.codec.http.HttpMethod.*;
import static  io.netty.handler.codec.http.HttpVersion.*;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-23
 * 
 */
public class WebSocketServer {

    
    public static void main(String[] args) {
        int port = 8080;
        new WebSocketServer().run(port);
    }
    
    public void run(int port){
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
              .channel(NioServerSocketChannel.class)
              .childHandler(new ChannelInitializer<NioSocketChannel>(){
                @Override
                protected void initChannel(NioSocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast("http-codec",new HttpServerCodec());
                    pipeline.addLast("http-aggregator", new HttpObjectAggregator(65535));
                    pipeline.addLast("http-chunked",new ChunkedWriteHandler());
                    pipeline.addLast("handler",new WebSocketServerHandler());
                }
              });
              Channel ch = b.bind(port).sync().channel();
              System.out.println("Web Socket Server started at port : " + port);
              System.out.println("Open you browser or navigate to http://localhost:" + port );
              ch.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally{
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
    
    static class WebSocketServerHandler extends SimpleChannelInboundHandler<Object>{

        private WebSocketServerHandshaker handshaker;
        
        @Override
        protected void messageReceived(ChannelHandlerContext ctx,Object msg) throws Exception {
            if(msg instanceof FullHttpRequest){
                handleHttpRequest(ctx,(FullHttpRequest)msg);
            }else if(msg instanceof WebSocketFrame){
                handleWebSocketFrame(ctx,(WebSocketFrame)msg);
            }
        }
        
        private void handleHttpRequest(ChannelHandlerContext ctx,FullHttpRequest request){
            //http解码失败，返回异常
            System.out.println("handleHttpRequest..");
            if(!request.decoderResult().isSuccess() ||
                    !"websocket".equals(request.headers().get("Upgrade"))){
                sendHttpResponse(ctx,request,new DefaultFullHttpResponse(HTTP_1_1,BAD_REQUEST));
                return ;
            }
            
            WebSocketServerHandshakerFactory factory = 
                    new WebSocketServerHandshakerFactory("ws://100.84.46.122:8080/websocket",null,false);
            handshaker = factory.newHandshaker(request);
            if(handshaker == null){
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            }else{
                handshaker.handshake(ctx.channel(), request);
            }
        }
        
        private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame ){
            //判断是否关闭链路指令
            if(frame instanceof CloseWebSocketFrame){
                handshaker.close(ctx.channel(), (CloseWebSocketFrame)frame.retain());
                return;
            }
            //判断是否ping消息
            if(frame instanceof PingWebSocketFrame){
                ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
                return ;
            }
            //本例只支持文件消息，不支持二进制消息
            if(!(frame instanceof TextWebSocketFrame)){
                throw new UnsupportedOperationException(String.format("%s frame types not supported.", frame.getClass().getName()));
            }
            //返回应答消息
            String request = ((TextWebSocketFrame)frame).text();
            ctx.channel().write(new TextWebSocketFrame(request + ",欢迎使用Netty WebSocket 服务，现在时刻：" + new Date().toString()));
            
        }
        
        private void sendHttpResponse(ChannelHandlerContext ctx,FullHttpRequest request,FullHttpResponse response){
            //返回应答给客户端
            if(response.status().code() != 200){
                ByteBuf buf = Unpooled.copiedBuffer(response.status().toString(),CharsetUtil.UTF_8);
                response.content().writeBytes(buf);
                buf.release();
                setContentLength(response,response.content().readableBytes());
            }
            ChannelFuture future = ctx.channel().writeAndFlush(response);
            //非keep-alive,关闭链接
            if(!isKeepAlive(request) || response.status().code() != 200){
                future.addListeners(ChannelFutureListener.CLOSE);
            }
                
        }
        
        private void setContentLength(HttpResponse response,long length){
            response.headers().set(CONTENT_LENGTH,String.valueOf(length));
        }

        private boolean isKeepAlive(FullHttpRequest req){
            return false;
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
