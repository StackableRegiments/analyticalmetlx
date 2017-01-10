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
public class PutSearchRequest extends com.amazonaws.opensdk.BaseRequest implements Serializable, Cloneable, RequestSignerAware {

    private SearchItemsPutRequest searchItemsPutRequest;

    /**
     * @param searchItemsPutRequest
     */

    public void setSearchItemsPutRequest(SearchItemsPutRequest searchItemsPutRequest) {
        this.searchItemsPutRequest = searchItemsPutRequest;
    }

    /**
     * @return
     */

    public SearchItemsPutRequest getSearchItemsPutRequest() {
        return this.searchItemsPutRequest;
    }

    /**
     * @param searchItemsPutRequest
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public PutSearchRequest searchItemsPutRequest(SearchItemsPutRequest searchItemsPutRequest) {
        setSearchItemsPutRequest(searchItemsPutRequest);
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
        if (getSearchItemsPutRequest() != null)
            sb.append("SearchItemsPutRequest: ").append(getSearchItemsPutRequest());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof PutSearchRequest == false)
            return false;
        PutSearchRequest other = (PutSearchRequest) obj;
        if (other.getSearchItemsPutRequest() == null ^ this.getSearchItemsPutRequest() == null)
            return false;
        if (other.getSearchItemsPutRequest() != null && other.getSearchItemsPutRequest().equals(this.getSearchItemsPutRequest()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getSearchItemsPutRequest() == null) ? 0 : getSearchItemsPutRequest().hashCode());
        return hashCode;
    }

    @Override
    public PutSearchRequest clone() {
        return (PutSearchRequest) super.clone();
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
    public PutSearchRequest sdkRequestConfig(com.amazonaws.opensdk.SdkRequestConfig sdkRequestConfig) {
        super.sdkRequestConfig(sdkRequestConfig);
        return this;
    }

}
