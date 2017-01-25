/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class PutSmartgroupsResult extends com.amazonaws.opensdk.BaseResult implements Serializable, Cloneable {

    private SmartGroupsResponse smartGroupsResponse;

    /**
     * @param smartGroupsResponse
     */

    public void setSmartGroupsResponse(SmartGroupsResponse smartGroupsResponse) {
        this.smartGroupsResponse = smartGroupsResponse;
    }

    /**
     * @return
     */

    public SmartGroupsResponse getSmartGroupsResponse() {
        return this.smartGroupsResponse;
    }

    /**
     * @param smartGroupsResponse
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public PutSmartgroupsResult smartGroupsResponse(SmartGroupsResponse smartGroupsResponse) {
        setSmartGroupsResponse(smartGroupsResponse);
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
        if (getSmartGroupsResponse() != null)
            sb.append("SmartGroupsResponse: ").append(getSmartGroupsResponse());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof PutSmartgroupsResult == false)
            return false;
        PutSmartgroupsResult other = (PutSmartgroupsResult) obj;
        if (other.getSmartGroupsResponse() == null ^ this.getSmartGroupsResponse() == null)
            return false;
        if (other.getSmartGroupsResponse() != null && other.getSmartGroupsResponse().equals(this.getSmartGroupsResponse()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getSmartGroupsResponse() == null) ? 0 : getSmartGroupsResponse().hashCode());
        return hashCode;
    }

    @Override
    public PutSmartgroupsResult clone() {
        try {
            return (PutSmartgroupsResult) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
