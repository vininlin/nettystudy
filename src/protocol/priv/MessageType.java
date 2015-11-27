/**
 * 
 */
package protocol.priv;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public enum MessageType {

    LOGIN_REQ("loginReq",(byte)1),
    LOGIN_RESP("loginResp",(byte)2),
    HEARTBEAT_REQ("heartBeatReq",(byte)3),
    HEARTBEAT_RESP("heartBeatResp",(byte)4);
    
    private String name;
    private byte value;
    
    public String getName(){
        return name;
    }
    
    public byte value(){
        return value;
    }
    private MessageType(String name,byte value){
        this.name = name;
        this.value = value;
    }
    
}
