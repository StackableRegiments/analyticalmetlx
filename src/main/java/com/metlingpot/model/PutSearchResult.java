/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class PutSearchResult extends com.amazonaws.opensdk.BaseResult implements Serializable, Cloneable {

    private java.util.List<SearchResponse> searchItemsResponse;

    /**
     * @return
     */

    public java.util.List<SearchResponse> getSearchItemsResponse() {
        return searchItemsResponse;
    }

    /**
     * @param searchItemsResponse
     */

    public void setSearchItemsResponse(java.util.Collection<SearchResponse> searchItemsResponse) {
        if (searchItemsResponse == null) {
            this.searchItemsResponse = null;
            return;
        }

        this.searchItemsResponse = new java.util.ArrayList<SearchResponse>(searchItemsResponse);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setSearchItemsResponse(java.util.Collection)} or {@link #withSearchItemsResponse(java.util.Collection)}
     * if you want to override the existing values.
     * </p>
     * 
     * @param searchItemsResponse
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public PutSearchResult searchItemsResponse(SearchResponse... searchItemsResponse) {
        if (this.searchItemsResponse == null) {
            setSearchItemsResponse(new java.util.ArrayList<SearchResponse>(searchItemsResponse.length));
        }
        for (SearchResponse ele : searchItemsResponse) {
            this.searchItemsResponse.add(ele);
        }
        return this;
    }

    /**
     * @param searchItemsResponse
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public PutSearchResult searchItemsResponse(java.util.Collection<SearchResponse> searchItemsResponse) {
        setSearchItemsResponse(searchItemsResponse);
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
        if (getSearchItemsResponse() != null)
            sb.append("SearchItemsResponse: ").append(getSearchItemsResponse());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof PutSearchResult == false)
            return false;
        PutSearchResult other = (PutSearchResult) obj;
        if (other.getSearchItemsResponse() == null ^ this.getSearchItemsResponse() == null)
            return false;
        if (other.getSearchItemsResponse() != null && other.getSearchItemsResponse().equals(this.getSearchItemsResponse()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getSearchItemsResponse() == null) ? 0 : getSearchItemsResponse().hashCode());
        return hashCode;
    }

    @Override
    public PutSearchResult clone() {
        try {
            return (PutSearchResult) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
