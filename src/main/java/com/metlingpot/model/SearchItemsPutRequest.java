/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class SearchItemsPutRequest implements Serializable, Cloneable {

    private Double after;

    private Double before;

    private Query query;

    /**
     * @param after
     */

    public void setAfter(Double after) {
        this.after = after;
    }

    /**
     * @return
     */

    public Double getAfter() {
        return this.after;
    }

    /**
     * @param after
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public SearchItemsPutRequest after(Double after) {
        setAfter(after);
        return this;
    }

    /**
     * @param before
     */

    public void setBefore(Double before) {
        this.before = before;
    }

    /**
     * @return
     */

    public Double getBefore() {
        return this.before;
    }

    /**
     * @param before
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public SearchItemsPutRequest before(Double before) {
        setBefore(before);
        return this;
    }

    /**
     * @param query
     */

    public void setQuery(Query query) {
        this.query = query;
    }

    /**
     * @return
     */

    public Query getQuery() {
        return this.query;
    }

    /**
     * @param query
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public SearchItemsPutRequest query(Query query) {
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
        if (getAfter() != null)
            sb.append("After: ").append(getAfter()).append(",");
        if (getBefore() != null)
            sb.append("Before: ").append(getBefore()).append(",");
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

        if (obj instanceof SearchItemsPutRequest == false)
            return false;
        SearchItemsPutRequest other = (SearchItemsPutRequest) obj;
        if (other.getAfter() == null ^ this.getAfter() == null)
            return false;
        if (other.getAfter() != null && other.getAfter().equals(this.getAfter()) == false)
            return false;
        if (other.getBefore() == null ^ this.getBefore() == null)
            return false;
        if (other.getBefore() != null && other.getBefore().equals(this.getBefore()) == false)
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

        hashCode = prime * hashCode + ((getAfter() == null) ? 0 : getAfter().hashCode());
        hashCode = prime * hashCode + ((getBefore() == null) ? 0 : getBefore().hashCode());
        hashCode = prime * hashCode + ((getQuery() == null) ? 0 : getQuery().hashCode());
        return hashCode;
    }

    @Override
    public SearchItemsPutRequest clone() {
        try {
            return (SearchItemsPutRequest) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
