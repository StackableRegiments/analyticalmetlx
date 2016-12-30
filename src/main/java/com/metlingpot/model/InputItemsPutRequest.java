/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class InputItemsPutRequest implements Serializable, Cloneable {

    private java.util.List<InputItem> items;

    /**
     * @return
     */

    public java.util.List<InputItem> getItems() {
        return items;
    }

    /**
     * @param items
     */

    public void setItems(java.util.Collection<InputItem> items) {
        if (items == null) {
            this.items = null;
            return;
        }

        this.items = new java.util.ArrayList<InputItem>(items);
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

    public InputItemsPutRequest items(InputItem... items) {
        if (this.items == null) {
            setItems(new java.util.ArrayList<InputItem>(items.length));
        }
        for (InputItem ele : items) {
            this.items.add(ele);
        }
        return this;
    }

    /**
     * @param items
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InputItemsPutRequest items(java.util.Collection<InputItem> items) {
        setItems(items);
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
        if (getItems() != null)
            sb.append("Items: ").append(getItems());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof InputItemsPutRequest == false)
            return false;
        InputItemsPutRequest other = (InputItemsPutRequest) obj;
        if (other.getItems() == null ^ this.getItems() == null)
            return false;
        if (other.getItems() != null && other.getItems().equals(this.getItems()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getItems() == null) ? 0 : getItems().hashCode());
        return hashCode;
    }

    @Override
    public InputItemsPutRequest clone() {
        try {
            return (InputItemsPutRequest) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
