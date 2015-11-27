/**
 * 
 */
package protocol.priv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public final class NettyMessageDecoder extends LengthFieldBasedFrameDecoder {

    private NettyMarshallingDecoder marshallingDecoder;
    
    /**
     * @param maxFrameLength
     * @param lengthFieldOffset
     * @param lengthFieldLength
     * @param lengthAdjustment
     * @param initialBytesToStrip
     */
    public NettyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
        marshallingDecoder = MarshallingCodeCFactory.buildMarshallingDecoder();
    }
    
    public NettyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength);
        marshallingDecoder = MarshallingCodeCFactory.buildMarshallingDecoder();
    }

    @Override
   protected Object decode(ChannelHandlerContext ctx , ByteBuf in) throws Exception{
       ByteBuf frame = (ByteBuf)super.decode(ctx, in);
       if(frame == null){
           return null;
       }
       NettyMessage message = new NettyMessage();
       Header header = new Header();
       header.setCrcCode(frame.readInt());
       header.setLength(frame.readInt());
       header.setSessionID(frame.readLong());
       header.setType(frame.readByte());
       header.setPriority(frame.readByte());
       
       int size = frame.readInt();
       if(size > 0){
           Map<String,Object> attachment = new HashMap<String,Object>(size);
           int keySize = 0;
           byte[] keyArray = null;
           String key = null;
           for(int i = 0 ; i < size ; i++){
               keySize = frame.readInt();
               keyArray = new byte[keySize];
               in.readBytes(keyArray);
               key = new String(keyArray,CharsetUtil.UTF_8);
               attachment.put(key, marshallingDecoder.decode(ctx, frame));
           }
           key = null;
           keyArray = null;
           header.setAttachment(attachment);
       }
       if(frame.readableBytes() > 4){
           message.setBody(marshallingDecoder.decode(ctx, frame));
       }
       message.setHeader(header);
       return message;
   }
   
}
