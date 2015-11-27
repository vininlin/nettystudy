/**
 * 
 */
package encodecode.marshalling;

import java.io.Serializable;

import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-19
 * 
 */
public class SubscribeResp implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -7824381588952552982L;

    private int subReqID;
    
    private int respCode;
    
    private String desc;

    /**
     * @return the subReqID
     */
    public int getSubReqID() {
        return subReqID;
    }

    /**
     * @param subReqID the subReqID to set
     */
    public void setSubReqID(int subReqID) {
        this.subReqID = subReqID;
    }

    /**
     * @return the respCode
     */
    public int getRespCode() {
        return respCode;
    }

    /**
     * @param respCode the respCode to set
     */
    public void setRespCode(int respCode) {
        this.respCode = respCode;
    }

    /**
     * @return the desc
     */
    public String getDesc() {
        return desc;
    }

    /**
     * @param desc the desc to set
     */
    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
    
}
