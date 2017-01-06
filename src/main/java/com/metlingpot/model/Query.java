/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class Query implements Serializable, Cloneable {

    private java.util.List<String> actionname;

    private java.util.List<String> actiontype;

    private java.util.List<String> actorname;

    private java.util.List<String> actortype;

    private java.util.List<String> contextname;

    private java.util.List<String> contexttype;

    private java.util.List<String> source;

    private java.util.List<String> targetname;

    private java.util.List<String> targettype;

    /**
     * @return
     */

    public java.util.List<String> getActionname() {
        return actionname;
    }

    /**
     * @param actionname
     */

    public void setActionname(java.util.Collection<String> actionname) {
        if (actionname == null) {
            this.actionname = null;
            return;
        }

        this.actionname = new java.util.ArrayList<String>(actionname);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setActionname(java.util.Collection)} or {@link #withActionname(java.util.Collection)} if you want to
     * override the existing values.
     * </p>
     * 
     * @param actionname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query actionname(String... actionname) {
        if (this.actionname == null) {
            setActionname(new java.util.ArrayList<String>(actionname.length));
        }
        for (String ele : actionname) {
            this.actionname.add(ele);
        }
        return this;
    }

    /**
     * @param actionname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query actionname(java.util.Collection<String> actionname) {
        setActionname(actionname);
        return this;
    }

    /**
     * @return
     */

    public java.util.List<String> getActiontype() {
        return actiontype;
    }

    /**
     * @param actiontype
     */

    public void setActiontype(java.util.Collection<String> actiontype) {
        if (actiontype == null) {
            this.actiontype = null;
            return;
        }

        this.actiontype = new java.util.ArrayList<String>(actiontype);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setActiontype(java.util.Collection)} or {@link #withActiontype(java.util.Collection)} if you want to
     * override the existing values.
     * </p>
     * 
     * @param actiontype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query actiontype(String... actiontype) {
        if (this.actiontype == null) {
            setActiontype(new java.util.ArrayList<String>(actiontype.length));
        }
        for (String ele : actiontype) {
            this.actiontype.add(ele);
        }
        return this;
    }

    /**
     * @param actiontype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query actiontype(java.util.Collection<String> actiontype) {
        setActiontype(actiontype);
        return this;
    }

    /**
     * @return
     */

    public java.util.List<String> getActorname() {
        return actorname;
    }

    /**
     * @param actorname
     */

    public void setActorname(java.util.Collection<String> actorname) {
        if (actorname == null) {
            this.actorname = null;
            return;
        }

        this.actorname = new java.util.ArrayList<String>(actorname);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setActorname(java.util.Collection)} or {@link #withActorname(java.util.Collection)} if you want to
     * override the existing values.
     * </p>
     * 
     * @param actorname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query actorname(String... actorname) {
        if (this.actorname == null) {
            setActorname(new java.util.ArrayList<String>(actorname.length));
        }
        for (String ele : actorname) {
            this.actorname.add(ele);
        }
        return this;
    }

    /**
     * @param actorname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query actorname(java.util.Collection<String> actorname) {
        setActorname(actorname);
        return this;
    }

    /**
     * @return
     */

    public java.util.List<String> getActortype() {
        return actortype;
    }

    /**
     * @param actortype
     */

    public void setActortype(java.util.Collection<String> actortype) {
        if (actortype == null) {
            this.actortype = null;
            return;
        }

        this.actortype = new java.util.ArrayList<String>(actortype);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setActortype(java.util.Collection)} or {@link #withActortype(java.util.Collection)} if you want to
     * override the existing values.
     * </p>
     * 
     * @param actortype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query actortype(String... actortype) {
        if (this.actortype == null) {
            setActortype(new java.util.ArrayList<String>(actortype.length));
        }
        for (String ele : actortype) {
            this.actortype.add(ele);
        }
        return this;
    }

    /**
     * @param actortype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query actortype(java.util.Collection<String> actortype) {
        setActortype(actortype);
        return this;
    }

    /**
     * @return
     */

    public java.util.List<String> getContextname() {
        return contextname;
    }

    /**
     * @param contextname
     */

    public void setContextname(java.util.Collection<String> contextname) {
        if (contextname == null) {
            this.contextname = null;
            return;
        }

        this.contextname = new java.util.ArrayList<String>(contextname);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setContextname(java.util.Collection)} or {@link #withContextname(java.util.Collection)} if you want to
     * override the existing values.
     * </p>
     * 
     * @param contextname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query contextname(String... contextname) {
        if (this.contextname == null) {
            setContextname(new java.util.ArrayList<String>(contextname.length));
        }
        for (String ele : contextname) {
            this.contextname.add(ele);
        }
        return this;
    }

    /**
     * @param contextname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query contextname(java.util.Collection<String> contextname) {
        setContextname(contextname);
        return this;
    }

    /**
     * @return
     */

    public java.util.List<String> getContexttype() {
        return contexttype;
    }

    /**
     * @param contexttype
     */

    public void setContexttype(java.util.Collection<String> contexttype) {
        if (contexttype == null) {
            this.contexttype = null;
            return;
        }

        this.contexttype = new java.util.ArrayList<String>(contexttype);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setContexttype(java.util.Collection)} or {@link #withContexttype(java.util.Collection)} if you want to
     * override the existing values.
     * </p>
     * 
     * @param contexttype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query contexttype(String... contexttype) {
        if (this.contexttype == null) {
            setContexttype(new java.util.ArrayList<String>(contexttype.length));
        }
        for (String ele : contexttype) {
            this.contexttype.add(ele);
        }
        return this;
    }

    /**
     * @param contexttype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query contexttype(java.util.Collection<String> contexttype) {
        setContexttype(contexttype);
        return this;
    }

    /**
     * @return
     */

    public java.util.List<String> getSource() {
        return source;
    }

    /**
     * @param source
     */

    public void setSource(java.util.Collection<String> source) {
        if (source == null) {
            this.source = null;
            return;
        }

        this.source = new java.util.ArrayList<String>(source);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setSource(java.util.Collection)} or {@link #withSource(java.util.Collection)} if you want to override the
     * existing values.
     * </p>
     * 
     * @param source
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query source(String... source) {
        if (this.source == null) {
            setSource(new java.util.ArrayList<String>(source.length));
        }
        for (String ele : source) {
            this.source.add(ele);
        }
        return this;
    }

    /**
     * @param source
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query source(java.util.Collection<String> source) {
        setSource(source);
        return this;
    }

    /**
     * @return
     */

    public java.util.List<String> getTargetname() {
        return targetname;
    }

    /**
     * @param targetname
     */

    public void setTargetname(java.util.Collection<String> targetname) {
        if (targetname == null) {
            this.targetname = null;
            return;
        }

        this.targetname = new java.util.ArrayList<String>(targetname);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setTargetname(java.util.Collection)} or {@link #withTargetname(java.util.Collection)} if you want to
     * override the existing values.
     * </p>
     * 
     * @param targetname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query targetname(String... targetname) {
        if (this.targetname == null) {
            setTargetname(new java.util.ArrayList<String>(targetname.length));
        }
        for (String ele : targetname) {
            this.targetname.add(ele);
        }
        return this;
    }

    /**
     * @param targetname
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query targetname(java.util.Collection<String> targetname) {
        setTargetname(targetname);
        return this;
    }

    /**
     * @return
     */

    public java.util.List<String> getTargettype() {
        return targettype;
    }

    /**
     * @param targettype
     */

    public void setTargettype(java.util.Collection<String> targettype) {
        if (targettype == null) {
            this.targettype = null;
            return;
        }

        this.targettype = new java.util.ArrayList<String>(targettype);
    }

    /**
     * <p>
     * <b>NOTE:</b> This method appends the values to the existing list (if any). Use
     * {@link #setTargettype(java.util.Collection)} or {@link #withTargettype(java.util.Collection)} if you want to
     * override the existing values.
     * </p>
     * 
     * @param targettype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query targettype(String... targettype) {
        if (this.targettype == null) {
            setTargettype(new java.util.ArrayList<String>(targettype.length));
        }
        for (String ele : targettype) {
            this.targettype.add(ele);
        }
        return this;
    }

    /**
     * @param targettype
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public Query targettype(java.util.Collection<String> targettype) {
        setTargettype(targettype);
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
        if (getSource() != null)
            sb.append("Source: ").append(getSource()).append(",");
        if (getTargetname() != null)
            sb.append("Targetname: ").append(getTargetname()).append(",");
        if (getTargettype() != null)
            sb.append("Targettype: ").append(getTargettype());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof Query == false)
            return false;
        Query other = (Query) obj;
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
        hashCode = prime * hashCode + ((getSource() == null) ? 0 : getSource().hashCode());
        hashCode = prime * hashCode + ((getTargetname() == null) ? 0 : getTargetname().hashCode());
        hashCode = prime * hashCode + ((getTargettype() == null) ? 0 : getTargettype().hashCode());
        return hashCode;
    }

    @Override
    public Query clone() {
        try {
            return (Query) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
