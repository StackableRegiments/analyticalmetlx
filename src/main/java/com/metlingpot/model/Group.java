/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class Group implements Serializable, Cloneable {

    private java.util.List<String> members;

    private String name;

    /**
     * @return
     */

    public java.util.List<String> getMembers() {
        return members;
    }

    /**
     * @param members
     */

    public void setMembers(java.util.Collection<String> members) {
        if (members == null) {
            this.members = null;
            return;
        }

        this.members = new java.util.ArrayList<String>(members);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setMembers(java.util.Collection)} or {@link #withMembers(java.util.Collection)} if you want to override
     * the existing values.
     * </p>
     * 
     * @param members
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Group members(String... members) {
        if (this.members == null) {
            setMembers(new java.util.ArrayList<String>(members.length));
        }
        for (String ele : members) {
            this.members.add(ele);
        }
        return this;
    }

    /**
     * @param members
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Group members(java.util.Collection<String> members) {
        setMembers(members);
        return this;
    }

    /**
     * @param name
     */

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return
     */

    public String getName() {
        return this.name;
    }

    /**
     * @param name
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Group name(String name) {
        setName(name);
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
        if (getMembers() != null)
            sb.append("Members: ").append(getMembers()).append(",");
        if (getName() != null)
            sb.append("Name: ").append(getName());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof Group == false)
            return false;
        Group other = (Group) obj;
        if (other.getMembers() == null ^ this.getMembers() == null)
            return false;
        if (other.getMembers() != null && other.getMembers().equals(this.getMembers()) == false)
            return false;
        if (other.getName() == null ^ this.getName() == null)
            return false;
        if (other.getName() != null && other.getName().equals(this.getName()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getMembers() == null) ? 0 : getMembers().hashCode());
        hashCode = prime * hashCode + ((getName() == null) ? 0 : getName().hashCode());
        return hashCode;
    }

    @Override
    public Group clone() {
        try {
            return (Group) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
