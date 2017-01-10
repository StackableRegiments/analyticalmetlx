/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class GetDBResponse implements Serializable, Cloneable {

    private String actionname;

    private String actiontype;

    private String actorname;

    private String actortype;

    private String contextname;

    private String contexttype;

    private Double id;

    private String source;

    private String targetname;

    private String targettype;

    private Double timestamp;

    private String value;

    /**
     * @param actionname
     */

    public void setActionname(String actionname) {
        this.actionname = actionname;
    }

    /**
     * @return
     */

    public String getActionname() {
        return this.actionname;
    }

    /**
     * @param actionname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse actionname(String actionname) {
        setActionname(actionname);
        return this;
    }

    /**
     * @param actiontype
     */

    public void setActiontype(String actiontype) {
        this.actiontype = actiontype;
    }

    /**
     * @return
     */

    public String getActiontype() {
        return this.actiontype;
    }

    /**
     * @param actiontype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse actiontype(String actiontype) {
        setActiontype(actiontype);
        return this;
    }

    /**
     * @param actorname
     */

    public void setActorname(String actorname) {
        this.actorname = actorname;
    }

    /**
     * @return
     */

    public String getActorname() {
        return this.actorname;
    }

    /**
     * @param actorname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse actorname(String actorname) {
        setActorname(actorname);
        return this;
    }

    /**
     * @param actortype
     */

    public void setActortype(String actortype) {
        this.actortype = actortype;
    }

    /**
     * @return
     */

    public String getActortype() {
        return this.actortype;
    }

    /**
     * @param actortype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse actortype(String actortype) {
        setActortype(actortype);
        return this;
    }

    /**
     * @param contextname
     */

    public void setContextname(String contextname) {
        this.contextname = contextname;
    }

    /**
     * @return
     */

    public String getContextname() {
        return this.contextname;
    }

    /**
     * @param contextname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse contextname(String contextname) {
        setContextname(contextname);
        return this;
    }

    /**
     * @param contexttype
     */

    public void setContexttype(String contexttype) {
        this.contexttype = contexttype;
    }

    /**
     * @return
     */

    public String getContexttype() {
        return this.contexttype;
    }

    /**
     * @param contexttype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse contexttype(String contexttype) {
        setContexttype(contexttype);
        return this;
    }

    /**
     * @param id
     */

    public void setId(Double id) {
        this.id = id;
    }

    /**
     * @return
     */

    public Double getId() {
        return this.id;
    }

    /**
     * @param id
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse id(Double id) {
        setId(id);
        return this;
    }

    /**
     * @param source
     */

    public void setSource(String source) {
        this.source = source;
    }

    /**
     * @return
     */

    public String getSource() {
        return this.source;
    }

    /**
     * @param source
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse source(String source) {
        setSource(source);
        return this;
    }

    /**
     * @param targetname
     */

    public void setTargetname(String targetname) {
        this.targetname = targetname;
    }

    /**
     * @return
     */

    public String getTargetname() {
        return this.targetname;
    }

    /**
     * @param targetname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse targetname(String targetname) {
        setTargetname(targetname);
        return this;
    }

    /**
     * @param targettype
     */

    public void setTargettype(String targettype) {
        this.targettype = targettype;
    }

    /**
     * @return
     */

    public String getTargettype() {
        return this.targettype;
    }

    /**
     * @param targettype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse targettype(String targettype) {
        setTargettype(targettype);
        return this;
    }

    /**
     * @param timestamp
     */

    public void setTimestamp(Double timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * @return
     */

    public Double getTimestamp() {
        return this.timestamp;
    }

    /**
     * @param timestamp
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse timestamp(Double timestamp) {
        setTimestamp(timestamp);
        return this;
    }

    /**
     * @param value
     */

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * @return
     */

    public String getValue() {
        return this.value;
    }

    /**
     * @param value
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public GetDBResponse value(String value) {
        setValue(value);
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
        if (getActionname() != null)
            sb.append("Actionname: ").append(getActionname()).append(",");
        if (getActiontype() != null)
            sb.append("Actiontype: ").append(getActiontype()).append(",");
        if (getActorname() != null)
            sb.append("Actorname: ").append(getActorname()).append(",");
        if (getActortype() != null)
            sb.append("Actortype: ").append(getActortype()).append(",");
        if (getContextname() != null)
            sb.append("Contextname: ").append(getContextname()).append(",");
        if (getContexttype() != null)
            sb.append("Contexttype: ").append(getContexttype()).append(",");
        if (getId() != null)
            sb.append("Id: ").append(getId()).append(",");
        if (getSource() != null)
            sb.append("Source: ").append(getSource()).append(",");
        if (getTargetname() != null)
            sb.append("Targetname: ").append(getTargetname()).append(",");
        if (getTargettype() != null)
            sb.append("Targettype: ").append(getTargettype()).append(",");
        if (getTimestamp() != null)
            sb.append("Timestamp: ").append(getTimestamp()).append(",");
        if (getValue() != null)
            sb.append("Value: ").append(getValue());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof GetDBResponse == false)
            return false;
        GetDBResponse other = (GetDBResponse) obj;
        if (other.getActionname() == null ^ this.getActionname() == null)
            return false;
        if (other.getActionname() != null && other.getActionname().equals(this.getActionname()) == false)
            return false;
        if (other.getActiontype() == null ^ this.getActiontype() == null)
            return false;
        if (other.getActiontype() != null && other.getActiontype().equals(this.getActiontype()) == false)
            return false;
        if (other.getActorname() == null ^ this.getActorname() == null)
            return false;
        if (other.getActorname() != null && other.getActorname().equals(this.getActorname()) == false)
            return false;
        if (other.getActortype() == null ^ this.getActortype() == null)
            return false;
        if (other.getActortype() != null && other.getActortype().equals(this.getActortype()) == false)
            return false;
        if (other.getContextname() == null ^ this.getContextname() == null)
            return false;
        if (other.getContextname() != null && other.getContextname().equals(this.getContextname()) == false)
            return false;
        if (other.getContexttype() == null ^ this.getContexttype() == null)
            return false;
        if (other.getContexttype() != null && other.getContexttype().equals(this.getContexttype()) == false)
            return false;
        if (other.getId() == null ^ this.getId() == null)
            return false;
        if (other.getId() != null && other.getId().equals(this.getId()) == false)
            return false;
        if (other.getSource() == null ^ this.getSource() == null)
            return false;
        if (other.getSource() != null && other.getSource().equals(this.getSource()) == false)
            return false;
        if (other.getTargetname() == null ^ this.getTargetname() == null)
            return false;
        if (other.getTargetname() != null && other.getTargetname().equals(this.getTargetname()) == false)
            return false;
        if (other.getTargettype() == null ^ this.getTargettype() == null)
            return false;
        if (other.getTargettype() != null && other.getTargettype().equals(this.getTargettype()) == false)
            return false;
        if (other.getTimestamp() == null ^ this.getTimestamp() == null)
            return false;
        if (other.getTimestamp() != null && other.getTimestamp().equals(this.getTimestamp()) == false)
            return false;
        if (other.getValue() == null ^ this.getValue() == null)
            return false;
        if (other.getValue() != null && other.getValue().equals(this.getValue()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getActionname() == null) ? 0 : getActionname().hashCode());
        hashCode = prime * hashCode + ((getActiontype() == null) ? 0 : getActiontype().hashCode());
        hashCode = prime * hashCode + ((getActorname() == null) ? 0 : getActorname().hashCode());
        hashCode = prime * hashCode + ((getActortype() == null) ? 0 : getActortype().hashCode());
        hashCode = prime * hashCode + ((getContextname() == null) ? 0 : getContextname().hashCode());
        hashCode = prime * hashCode + ((getContexttype() == null) ? 0 : getContexttype().hashCode());
        hashCode = prime * hashCode + ((getId() == null) ? 0 : getId().hashCode());
        hashCode = prime * hashCode + ((getSource() == null) ? 0 : getSource().hashCode());
        hashCode = prime * hashCode + ((getTargetname() == null) ? 0 : getTargetname().hashCode());
        hashCode = prime * hashCode + ((getTargettype() == null) ? 0 : getTargettype().hashCode());
        hashCode = prime * hashCode + ((getTimestamp() == null) ? 0 : getTimestamp().hashCode());
        hashCode = prime * hashCode + ((getValue() == null) ? 0 : getValue().hashCode());
        return hashCode;
    }

    @Override
    public GetDBResponse clone() {
        try {
            return (GetDBResponse) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
