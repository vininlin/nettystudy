/**
 * 
 */
package protocol.priv;

import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * 
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public final class NettyMessage {

    private Header header;
    
    private Object body;
    
    /**
     * @return the header
     */
    public Header getHeader() {
        return header;
    }
    
    /**
     * @param header the header to set
     */
    public void setHeader(Header header) {
        this.header = header;
    }

    /**
     * @return the body
     */
    public Object getBody() {
        return body;
    }

    /**
     * @param body the body to set
     */
    public void setBody(Object body) {
        this.body = body;
    }
    
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
    
}
