/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.auth.RequestSigner;
import com.amazonaws.opensdk.protect.auth.RequestSignerAware;

/**
 * 
 */
public class PutSmartgroupsRequest extends com.amazonaws.opensdk.BaseRequest implements Serializable, Cloneable, RequestSignerAware {

    private SmartGroupsRequest smartGroupsRequest;

    /**
     * @param smartGroupsRequest
     */

    public void setSmartGroupsRequest(SmartGroupsRequest smartGroupsRequest) {
        this.smartGroupsRequest = smartGroupsRequest;
    }

    /**
     * @return
     */

    public SmartGroupsRequest getSmartGroupsRequest() {
        return this.smartGroupsRequest;
    }

    /**
     * @param smartGroupsRequest
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public PutSmartgroupsRequest smartGroupsRequest(SmartGroupsRequest smartGroupsRequest) {
        setSmartGroupsRequest(smartGroupsRequest);
        return this;
    }

    /**
     * Returns a string representation of this object; useful for testing and debugging.
     *
     * @return A string representation of this object.
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (getSmartGroupsRequest() != null)
            sb.append("SmartGroupsRequest: ").append(getSmartGroupsRequest());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof PutSmartgroupsRequest == false)
            return false;
        PutSmartgroupsRequest other = (PutSmartgroupsRequest) obj;
        if (other.getSmartGroupsRequest() == null ^ this.getSmartGroupsRequest() == null)
            return false;
        if (other.getSmartGroupsRequest() != null && other.getSmartGroupsRequest().equals(this.getSmartGroupsRequest()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getSmartGroupsRequest() == null) ? 0 : getSmartGroupsRequest().hashCode());
        return hashCode;
    }

    @Override
    public PutSmartgroupsRequest clone() {
        return (PutSmartgroupsRequest) super.clone();
    }

    @Override
    public Class<? extends RequestSigner> signerType() {
        return com.amazonaws.opensdk.protect.auth.IamRequestSigner.class;
    }

    /**
     * Set the configuration for this request.
     *
     * @param sdkRequestConfig
     *        Request configuration.
     * @return This object for method chaining.
     */
    public PutSmartgroupsRequest sdkRequestConfig(com.amazonaws.opensdk.SdkRequestConfig sdkRequestConfig) {
        super.sdkRequestConfig(sdkRequestConfig);
        return this;
    }

}
