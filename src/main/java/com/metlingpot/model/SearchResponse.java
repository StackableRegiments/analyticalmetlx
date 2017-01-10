/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class SearchResponse implements Serializable, Cloneable {

    private Double count;

    private java.util.List<GetDBResponse> items;

    private String query;

    /**
     * @param count
     */

    public void setCount(Double count) {
        this.count = count;
    }

    /**
     * @return
     */

    public Double getCount() {
        return this.count;
    }

    /**
     * @param count
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public SearchResponse count(Double count) {
        setCount(count);
        return this;
    }

    /**
     * @return
     */

    public java.util.List<GetDBResponse> getItems() {
        return items;
    }

    /**
     * @param items
     */

    public void setItems(java.util.Collection<GetDBResponse> items) {
        if (items == null) {
            this.items = null;
            return;
        }

        this.items = new java.util.ArrayList<GetDBResponse>(items);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setItems(java.util.Collection)} or {@link #withItems(java.util.Collection)} if you want to override the
     * existing values.
     * </p>
     * 
     * @param items
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public SearchResponse items(GetDBResponse... items) {
        if (this.items == null) {
            setItems(new java.util.ArrayList<GetDBResponse>(items.length));
        }
        for (GetDBResponse ele : items) {
            this.items.add(ele);
        }
        return this;
    }

    /**
     * @param items
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public SearchResponse items(java.util.Collection<GetDBResponse> items) {
        setItems(items);
        return this;
    }

    /**
     * @param query
     */

    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * @return
     */

    public String getQuery() {
        return this.query;
    }

    /**
     * @param query
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public SearchResponse query(String query) {
        setQuery(query);
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
        if (getCount() != null)
            sb.append("Count: ").append(getCount()).append(",");
        if (getItems() != null)
            sb.append("Items: ").append(getItems()).append(",");
        if (getQuery() != null)
            sb.append("Query: ").append(getQuery());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof SearchResponse == false)
            return false;
        SearchResponse other = (SearchResponse) obj;
        if (other.getCount() == null ^ this.getCount() == null)
            return false;
        if (other.getCount() != null && other.getCount().equals(this.getCount()) == false)
            return false;
        if (other.getItems() == null ^ this.getItems() == null)
            return false;
        if (other.getItems() != null && other.getItems().equals(this.getItems()) == false)
            return false;
        if (other.getQuery() == null ^ this.getQuery() == null)
            return false;
        if (other.getQuery() != null && other.getQuery().equals(this.getQuery()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getCount() == null) ? 0 : getCount().hashCode());
        hashCode = prime * hashCode + ((getItems() == null) ? 0 : getItems().hashCode());
        hashCode = prime * hashCode + ((getQuery() == null) ? 0 : getQuery().hashCode());
        return hashCode;
    }

    @Override
    public SearchResponse clone() {
        try {
            return (SearchResponse) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
