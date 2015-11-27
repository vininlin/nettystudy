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
 * @createDate 2015-11-24
 * 
 */
public class LoginAuthReqHandler extends ChannelHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("buildLoginReq..");
        ctx.writeAndFlush(buildLoginReq());
    }

    private NettyMessage buildLoginReq(){
        NettyMessage message = new NettyMessage();
        Header header = new Header();
        header.setType(MessageType.LOGIN_REQ.value());
        message.setHeader(header);
        return message;
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyMessage message = (NettyMessage)msg;
        if(message.getHeader() != null && message.getHeader().getType() == MessageType.LOGIN_RESP.value()){
            byte loginResult = (byte)message.getBody();
            if(loginResult != (byte)0){
                //失败，关闭连接
                ctx.close();
            }else{
                System.out.println("Login is OK :" + message);
                ctx.fireChannelRead(msg);
            }
        }else{
            ctx.fireChannelRead(msg);
        }
        
    }

    
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
       ctx.flush();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.fireExceptionCaught(cause);
    }

}
