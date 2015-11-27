/**
 * 
 */
package protocol.priv;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.marshalling.MarshallingDecoder;
import io.netty.handler.codec.marshalling.UnmarshallerProvider;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public class NettyMarshallingDecoder extends MarshallingDecoder {

    /**
     * @param provider
     */
    public NettyMarshallingDecoder(UnmarshallerProvider provider) {
        super(provider);
    }
    
    public NettyMarshallingDecoder(UnmarshallerProvider provider,int maxObjectSize) {
        super(provider,maxObjectSize);
    }
    
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception{
        return super.decode(ctx, in);
    }

}
