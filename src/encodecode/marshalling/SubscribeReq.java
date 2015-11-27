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
public class SubscribeReq implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1399340672798729269L;
    
    private int subReqId ;
    
    private String userName;
    
    private String productName;
    
    private String phoneNumber;
    
    private String address;

    /**
     * @return the subReqId
     */
    public int getSubReqId() {
        return subReqId;
    }

    /**
     * @param subReqId the subReqId to set
     */
    public void setSubReqId(int subReqId) {
        this.subReqId = subReqId;
    }

    /**
     * @return the userName
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @param userName the userName to set
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * @return the productName
     */
    public String getProductName() {
        return productName;
    }

    /**
     * @param productName the productName to set
     */
    public void setProductName(String productName) {
        this.productName = productName;
    }

    /**
     * @return the phoneNumber
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * @param phoneNumber the phoneNumber to set
     */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /**
     * @return the address
     */
    public String getAddress() {
        return address;
    }

    /**
     * @param address the address to set
     */
    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
