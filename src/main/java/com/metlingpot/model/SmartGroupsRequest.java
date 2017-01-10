/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class SmartGroupsRequest implements Serializable, Cloneable {

    private Double groupCount;

    private java.util.List<String> members;

    /**
     * @param groupCount
     */

    public void setGroupCount(Double groupCount) {
        this.groupCount = groupCount;
    }

    /**
     * @return
     */

    public Double getGroupCount() {
        return this.groupCount;
    }

    /**
     * @param groupCount
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public SmartGroupsRequest groupCount(Double groupCount) {
        setGroupCount(groupCount);
        return this;
    }

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

    public SmartGroupsRequest members(String... members) {
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

    public SmartGroupsRequest members(java.util.Collection<String> members) {
        setMembers(members);
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
        if (getGroupCount() != null)
            sb.append("GroupCount: ").append(getGroupCount()).append(",");
        if (getMembers() != null)
            sb.append("Members: ").append(getMembers());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof SmartGroupsRequest == false)
            return false;
        SmartGroupsRequest other = (SmartGroupsRequest) obj;
        if (other.getGroupCount() == null ^ this.getGroupCount() == null)
            return false;
        if (other.getGroupCount() != null && other.getGroupCount().equals(this.getGroupCount()) == false)
            return false;
        if (other.getMembers() == null ^ this.getMembers() == null)
            return false;
        if (other.getMembers() != null && other.getMembers().equals(this.getMembers()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getGroupCount() == null) ? 0 : getGroupCount().hashCode());
        hashCode = prime * hashCode + ((getMembers() == null) ? 0 : getMembers().hashCode());
        return hashCode;
    }

    @Override
    public SmartGroupsRequest clone() {
        try {
            return (SmartGroupsRequest) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
