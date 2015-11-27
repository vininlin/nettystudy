/**
 * 
 */
package protocol.priv;

import java.io.IOException;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;

import io.netty.handler.codec.marshalling.DefaultMarshallerProvider;
import io.netty.handler.codec.marshalling.DefaultUnmarshallerProvider;
import io.netty.handler.codec.marshalling.MarshallerProvider;
import io.netty.handler.codec.marshalling.MarshallingDecoder;
import io.netty.handler.codec.marshalling.MarshallingEncoder;
import io.netty.handler.codec.marshalling.UnmarshallerProvider;

/**
 * 
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-20
 * 
 */
public final class MarshallingCodeCFactory {

    public static NettyMarshallingDecoder buildMarshallingDecoder(){
        final MarshallerFactory factory = Marshalling.getProvidedMarshallerFactory("serial");
        final MarshallingConfiguration config = new MarshallingConfiguration();
        config.setVersion(5);
        UnmarshallerProvider provider = new DefaultUnmarshallerProvider(factory,config);
        NettyMarshallingDecoder decoder = new NettyMarshallingDecoder(provider,1024);
        return decoder;
    }
    
    public static NettyMarshallingEncoder buildMarshallingEncoder(){
        final MarshallerFactory factory = Marshalling.getProvidedMarshallerFactory("serial");
        final MarshallingConfiguration config = new MarshallingConfiguration();
        config.setVersion(5);
        MarshallerProvider provider = new DefaultMarshallerProvider(factory,config);
        NettyMarshallingEncoder encoder = new NettyMarshallingEncoder(provider);
        return encoder;
    }
    
}
