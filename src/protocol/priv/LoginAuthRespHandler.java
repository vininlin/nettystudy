/**
 * 
 */
package protocol.priv;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public class LoginAuthRespHandler extends ChannelHandlerAdapter {
    
    private Map<String,Boolean> nodeCheck = new ConcurrentHashMap<String,Boolean>();
    private String[] whiteList = {"127.0.0.1","100.84.46.122"}; 

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("Receive message from client");
        //TimeUnit.SECONDS.sleep(20);
        NettyMessage message = (NettyMessage)msg;
        if(message.getHeader() != null && message.getHeader().getType() == MessageType.LOGIN_REQ.value()){
            String nodeIndex = ctx.channel().remoteAddress().toString();
            NettyMessage loginResp = null;
            //重复登录
            if(nodeCheck.containsKey(nodeIndex)){
                loginResp = buildLoginResp((byte)-1);
            }else{
                InetSocketAddress address = (InetSocketAddress)ctx.channel().remoteAddress();
                String ip = address.getAddress().getHostAddress();
                boolean isOK = false;
                for(String wip : whiteList){
                    if(wip.equals(ip)){
                        isOK = true;
                        break;
                    }
                }
                loginResp = isOK ? buildLoginResp((byte)0) :buildLoginResp((byte)-1);
                if(isOK){
                    nodeCheck.put(nodeIndex, true);
                }
            }
            System.out.println("Login response is :" + loginResp + "body [" + loginResp.getBody() + "]");
            ctx.writeAndFlush(loginResp);
        }else{
            ctx.fireChannelRead(msg);
        }
        
    }

    private NettyMessage buildLoginResp(byte result){
        NettyMessage message = new NettyMessage();
        Header header = new Header();
        header.setType(MessageType.LOGIN_RESP.value());
        message.setHeader(header);
        message.setBody(result);
        return message;
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        nodeCheck.remove(ctx.channel().remoteAddress().toString());
        ctx.close();
        ctx.fireExceptionCaught(cause);
    }

}
