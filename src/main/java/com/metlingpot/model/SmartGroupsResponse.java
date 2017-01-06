/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class SmartGroupsResponse implements Serializable, Cloneable {

    private java.util.List<GroupSet> groupSets;

    private java.util.List<String> members;

    /**
     * @return
     */

    public java.util.List<GroupSet> getGroupSets() {
        return groupSets;
    }

    /**
     * @param groupSets
     */

    public void setGroupSets(java.util.Collection<GroupSet> groupSets) {
        if (groupSets == null) {
            this.groupSets = null;
            return;
        }

        this.groupSets = new java.util.ArrayList<GroupSet>(groupSets);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setGroupSets(java.util.Collection)} or {@link #withGroupSets(java.util.Collection)} if you want to
     * override the existing values.
     * </p>
     * 
     * @param groupSets
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public SmartGroupsResponse groupSets(GroupSet... groupSets) {
        if (this.groupSets == null) {
            setGroupSets(new java.util.ArrayList<GroupSet>(groupSets.length));
        }
        for (GroupSet ele : groupSets) {
            this.groupSets.add(ele);
        }
        return this;
    }

    /**
     * @param groupSets
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public SmartGroupsResponse groupSets(java.util.Collection<GroupSet> groupSets) {
        setGroupSets(groupSets);
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

    public SmartGroupsResponse members(String... members) {
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

    public SmartGroupsResponse members(java.util.Collection<String> members) {
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
        if (getGroupSets() != null)
            sb.append("GroupSets: ").append(getGroupSets()).append(",");
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

        if (obj instanceof SmartGroupsResponse == false)
            return false;
        SmartGroupsResponse other = (SmartGroupsResponse) obj;
        if (other.getGroupSets() == null ^ this.getGroupSets() == null)
            return false;
        if (other.getGroupSets() != null && other.getGroupSets().equals(this.getGroupSets()) == false)
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

        hashCode = prime * hashCode + ((getGroupSets() == null) ? 0 : getGroupSets().hashCode());
        hashCode = prime * hashCode + ((getMembers() == null) ? 0 : getMembers().hashCode());
        return hashCode;
    }

    @Override
    public SmartGroupsResponse clone() {
        try {
            return (SmartGroupsResponse) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
