/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class InsertResponse implements Serializable, Cloneable {

    private InsertDBResponse dbResponse;

    private Double index;

    private Item item;

    /**
     * @param dbResponse
     */

    public void setDbResponse(InsertDBResponse dbResponse) {
        this.dbResponse = dbResponse;
    }

    /**
     * @return
     */

    public InsertDBResponse getDbResponse() {
        return this.dbResponse;
    }

    /**
     * @param dbResponse
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertResponse dbResponse(InsertDBResponse dbResponse) {
        setDbResponse(dbResponse);
        return this;
    }

    /**
     * @param index
     */

    public void setIndex(Double index) {
        this.index = index;
    }

    /**
     * @return
     */

    public Double getIndex() {
        return this.index;
    }

    /**
     * @param index
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertResponse index(Double index) {
        setIndex(index);
        return this;
    }

    /**
     * @param item
     */

    public void setItem(Item item) {
        this.item = item;
    }

    /**
     * @return
     */

    public Item getItem() {
        return this.item;
    }

    /**
     * @param item
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertResponse item(Item item) {
        setItem(item);
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
        if (getDbResponse() != null)
            sb.append("DbResponse: ").append(getDbResponse()).append(",");
        if (getIndex() != null)
            sb.append("Index: ").append(getIndex()).append(",");
        if (getItem() != null)
            sb.append("Item: ").append(getItem());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof InsertResponse == false)
            return false;
        InsertResponse other = (InsertResponse) obj;
        if (other.getDbResponse() == null ^ this.getDbResponse() == null)
            return false;
        if (other.getDbResponse() != null && other.getDbResponse().equals(this.getDbResponse()) == false)
            return false;
        if (other.getIndex() == null ^ this.getIndex() == null)
            return false;
        if (other.getIndex() != null && other.getIndex().equals(this.getIndex()) == false)
            return false;
        if (other.getItem() == null ^ this.getItem() == null)
            return false;
        if (other.getItem() != null && other.getItem().equals(this.getItem()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getDbResponse() == null) ? 0 : getDbResponse().hashCode());
        hashCode = prime * hashCode + ((getIndex() == null) ? 0 : getIndex().hashCode());
        hashCode = prime * hashCode + ((getItem() == null) ? 0 : getItem().hashCode());
        return hashCode;
    }

    @Override
    public InsertResponse clone() {
        try {
            return (InsertResponse) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
