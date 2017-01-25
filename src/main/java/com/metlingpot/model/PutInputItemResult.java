/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class PutInputItemResult extends com.amazonaws.opensdk.BaseResult implements Serializable, Cloneable {

    private java.util.List<InsertResponse> inputItemsResponse;

    /**
     * @return
     */

    public java.util.List<InsertResponse> getInputItemsResponse() {
        return inputItemsResponse;
    }

    /**
     * @param inputItemsResponse
     */

    public void setInputItemsResponse(java.util.Collection<InsertResponse> inputItemsResponse) {
        if (inputItemsResponse == null) {
            this.inputItemsResponse = null;
            return;
        }

        this.inputItemsResponse = new java.util.ArrayList<InsertResponse>(inputItemsResponse);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setInputItemsResponse(java.util.Collection)} or {@link #withInputItemsResponse(java.util.Collection)} if
     * you want to override the existing values.
     * </p>
     * 
     * @param inputItemsResponse
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public PutInputItemResult inputItemsResponse(InsertResponse... inputItemsResponse) {
        if (this.inputItemsResponse == null) {
            setInputItemsResponse(new java.util.ArrayList<InsertResponse>(inputItemsResponse.length));
        }
        for (InsertResponse ele : inputItemsResponse) {
            this.inputItemsResponse.add(ele);
        }
        return this;
    }

    /**
     * @param inputItemsResponse
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public PutInputItemResult inputItemsResponse(java.util.Collection<InsertResponse> inputItemsResponse) {
        setInputItemsResponse(inputItemsResponse);
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
        if (getInputItemsResponse() != null)
            sb.append("InputItemsResponse: ").append(getInputItemsResponse());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof PutInputItemResult == false)
            return false;
        PutInputItemResult other = (PutInputItemResult) obj;
        if (other.getInputItemsResponse() == null ^ this.getInputItemsResponse() == null)
            return false;
        if (other.getInputItemsResponse() != null && other.getInputItemsResponse().equals(this.getInputItemsResponse()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getInputItemsResponse() == null) ? 0 : getInputItemsResponse().hashCode());
        return hashCode;
    }

    @Override
    public PutInputItemResult clone() {
        try {
            return (PutInputItemResult) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
