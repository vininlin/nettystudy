/**
 * 
 */
package protocol.priv;

import java.util.List;
import java.util.Map.Entry;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.CharsetUtil;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public final class NettyMessageEncoder extends MessageToMessageEncoder<NettyMessage> {

    private NettyMarshallingEncoder marshallingEncoder;
    
    public NettyMessageEncoder(){
        marshallingEncoder = MarshallingCodeCFactory.buildMarshallingEncoder();
    }
   
    @Override
    protected void encode(ChannelHandlerContext ctx, NettyMessage msg, List<Object> out)
            throws Exception {
        if(msg == null || msg.getHeader() == null){
            throw new Exception("the encode message is null");
        }
        ByteBuf sendBuf = Unpooled.buffer();
        sendBuf.writeInt(msg.getHeader().getCrcCode());
        sendBuf.writeInt(msg.getHeader().getLength());
        sendBuf.writeLong(msg.getHeader().getSessionID());
        sendBuf.writeByte(msg.getHeader().getType());
        sendBuf.writeByte(msg.getHeader().getPriority());
        sendBuf.writeInt(msg.getHeader().getAttachment().size());
        String key = null;
        byte[] keyArray = null;
        Object value = null;
        for(Entry<String, Object> param : msg.getHeader().getAttachment().entrySet()){
            key = param.getKey();
            keyArray = key.getBytes(CharsetUtil.UTF_8);
            sendBuf.writeInt(keyArray.length);
            sendBuf.writeBytes(keyArray);
            value = param.getValue();
            marshallingEncoder.encode(ctx, value, sendBuf);
        }
        key = null;
        keyArray = null;
        value = null;
        
        if(msg.getBody() != null){
            marshallingEncoder.encode(ctx, msg.getBody(), sendBuf);
        }else{
            sendBuf.writeInt(0);
        }
        //第4个字节写入总长度
        sendBuf.setInt(4,sendBuf.readableBytes());
        out.add(sendBuf);
    }

}
