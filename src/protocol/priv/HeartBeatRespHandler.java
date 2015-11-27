/**
 * 
 */
package protocol.priv;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-25
 * 
 */
public class HeartBeatRespHandler extends ChannelHandlerAdapter {

    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyMessage message = (NettyMessage)msg;
        if(message.getHeader() != null 
                && message.getHeader().getType() == MessageType.HEARTBEAT_REQ.value()){
            System.out.println("Server receive client heart beat message ----> " + message );
            NettyMessage heartBeat = buildHeartBeat();
            System.out.println("Server send heart beat message to client:  ----> " + heartBeat );
            ctx.writeAndFlush(heartBeat);
        }else{
            ctx.fireChannelRead(msg);
        }
    }
    
    private NettyMessage buildHeartBeat(){
        NettyMessage message = new NettyMessage();
        Header header = new Header();
        header.setType(MessageType.HEARTBEAT_RESP.value());
        message.setHeader(header);
        return message;
    }
    

}
