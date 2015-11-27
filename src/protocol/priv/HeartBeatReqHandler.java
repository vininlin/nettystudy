/**
 * 
 */
package protocol.priv;

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-25
 * 
 */
public class HeartBeatReqHandler extends ChannelHandlerAdapter {

    private volatile ScheduledFuture<?> heartBeat;
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyMessage message = (NettyMessage)msg;
        //握手成功，发送心跳
        if(message.getHeader() != null 
                && message.getHeader().getType() == MessageType.LOGIN_RESP.value()){
            heartBeat = ctx.executor().scheduleAtFixedRate(new HeartBeatTask(ctx), 0, 5000, TimeUnit.MILLISECONDS);
        }else if(message.getHeader() != null 
                && message.getHeader().getType() == MessageType.HEARTBEAT_RESP.value()){
            System.out.println("Client receive server heart beat message ----> " + message );
        }else{
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if(heartBeat != null){
            heartBeat.cancel(true);
            heartBeat = null;
        }
        //ctx.fireExceptionCaught(cause);
        cause.printStackTrace();
    }
    
    private static class HeartBeatTask implements Runnable{

        private final ChannelHandlerContext ctx;
        
        public HeartBeatTask(ChannelHandlerContext ctx){
            this.ctx = ctx;
        }
        
        @Override
        public void run() {
            NettyMessage message = buildHeartBeat();
            System.out.println("Client send heart beat message to server:  ----> " + message );
            ctx.writeAndFlush(message);
        }
        
        private NettyMessage buildHeartBeat(){
            NettyMessage message = new NettyMessage();
            Header header = new Header();
            header.setType(MessageType.HEARTBEAT_REQ.value());
            message.setHeader(header);
            return message;
        }
        
    }
}
