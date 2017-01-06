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
public class PutInputItemRequest extends com.amazonaws.opensdk.BaseRequest implements Serializable, Cloneable, RequestSignerAware {

    private InputItemsPutRequest inputItemsPutRequest;

    /**
     * @param inputItemsPutRequest
     */

    public void setInputItemsPutRequest(InputItemsPutRequest inputItemsPutRequest) {
        this.inputItemsPutRequest = inputItemsPutRequest;
    }

    /**
     * @return
     */

    public InputItemsPutRequest getInputItemsPutRequest() {
        return this.inputItemsPutRequest;
    }

    /**
     * @param inputItemsPutRequest
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public PutInputItemRequest inputItemsPutRequest(InputItemsPutRequest inputItemsPutRequest) {
        setInputItemsPutRequest(inputItemsPutRequest);
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
        if (getInputItemsPutRequest() != null)
            sb.append("InputItemsPutRequest: ").append(getInputItemsPutRequest());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof PutInputItemRequest == false)
            return false;
        PutInputItemRequest other = (PutInputItemRequest) obj;
        if (other.getInputItemsPutRequest() == null ^ this.getInputItemsPutRequest() == null)
            return false;
        if (other.getInputItemsPutRequest() != null && other.getInputItemsPutRequest().equals(this.getInputItemsPutRequest()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getInputItemsPutRequest() == null) ? 0 : getInputItemsPutRequest().hashCode());
        return hashCode;
    }

    @Override
    public PutInputItemRequest clone() {
        return (PutInputItemRequest) super.clone();
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
    public PutInputItemRequest sdkRequestConfig(com.amazonaws.opensdk.SdkRequestConfig sdkRequestConfig) {
        super.sdkRequestConfig(sdkRequestConfig);
        return this;
    }

}
