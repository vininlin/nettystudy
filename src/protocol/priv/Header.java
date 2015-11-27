/**
 * 
 */
package protocol.priv;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public final class Header {

    private int crcCode = 0xabef0101;
    //消息长度
    private int length;
    //会话ID
    private long sessionID;
    //消息类型
    private byte type;
    //消息优先级
    private byte priority;
    //附件
    private Map<String,Object> attachment = new HashMap<String,Object>();
    
    /**
     * @return the crcCode
     */
    public int getCrcCode() {
        return crcCode;
    }
    /**
     * @param crcCode the crcCode to set
     */
    public void setCrcCode(int crcCode) {
        this.crcCode = crcCode;
    }
    /**
     * @return the length
     */
    public int getLength() {
        return length;
    }
    /**
     * @param length the length to set
     */
    public void setLength(int length) {
        this.length = length;
    }
    /**
     * @return the sessionID
     */
    public long getSessionID() {
        return sessionID;
    }
    /**
     * @param sessionID the sessionID to set
     */
    public void setSessionID(long sessionID) {
        this.sessionID = sessionID;
    }
    /**
     * @return the type
     */
    public byte getType() {
        return type;
    }
    /**
     * @param type the type to set
     */
    public void setType(byte type) {
        this.type = type;
    }
    /**
     * @return the priority
     */
    public byte getPriority() {
        return priority;
    }
    /**
     * @param priority the priority to set
     */
    public void setPriority(byte priority) {
        this.priority = priority;
    }
    /**
     * @return the attachment
     */
    public Map<String, Object> getAttachment() {
        return attachment;
    }
    /**
     * @param attachment the attachment to set
     */
    public void setAttachment(Map<String, Object> attachment) {
        this.attachment = attachment;
    }
   
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
    
}
