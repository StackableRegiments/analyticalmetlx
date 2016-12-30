/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class InputItem implements Serializable, Cloneable {

    private TypedValue action;

    private TypedValue actor;

    private TypedValue context;

    private String source;

    private TypedValue target;

    private Double timestamp;

    private String value;

    /**
     * @param action
     */

    public void setAction(TypedValue action) {
        this.action = action;
    }

    /**
     * @return
     */

    public TypedValue getAction() {
        return this.action;
    }

    /**
     * @param action
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InputItem action(TypedValue action) {
        setAction(action);
        return this;
    }

    /**
     * @param actor
     */

    public void setActor(TypedValue actor) {
        this.actor = actor;
    }

    /**
     * @return
     */

    public TypedValue getActor() {
        return this.actor;
    }

    /**
     * @param actor
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InputItem actor(TypedValue actor) {
        setActor(actor);
        return this;
    }

    /**
     * @param context
     */

    public void setContext(TypedValue context) {
        this.context = context;
    }

    /**
     * @return
     */

    public TypedValue getContext() {
        return this.context;
    }

    /**
     * @param context
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InputItem context(TypedValue context) {
        setContext(context);
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

    public InputItem source(String source) {
        setSource(source);
        return this;
    }

    /**
     * @param target
     */

    public void setTarget(TypedValue target) {
        this.target = target;
    }

    /**
     * @return
     */

    public TypedValue getTarget() {
        return this.target;
    }

    /**
     * @param target
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InputItem target(TypedValue target) {
        setTarget(target);
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

    public InputItem timestamp(Double timestamp) {
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

    public InputItem value(String value) {
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
        if (getAction() != null)
            sb.append("Action: ").append(getAction()).append(",");
        if (getActor() != null)
            sb.append("Actor: ").append(getActor()).append(",");
        if (getContext() != null)
            sb.append("Context: ").append(getContext()).append(",");
        if (getSource() != null)
            sb.append("Source: ").append(getSource()).append(",");
        if (getTarget() != null)
            sb.append("Target: ").append(getTarget()).append(",");
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

        if (obj instanceof InputItem == false)
            return false;
        InputItem other = (InputItem) obj;
        if (other.getAction() == null ^ this.getAction() == null)
            return false;
        if (other.getAction() != null && other.getAction().equals(this.getAction()) == false)
            return false;
        if (other.getActor() == null ^ this.getActor() == null)
            return false;
        if (other.getActor() != null && other.getActor().equals(this.getActor()) == false)
            return false;
        if (other.getContext() == null ^ this.getContext() == null)
            return false;
        if (other.getContext() != null && other.getContext().equals(this.getContext()) == false)
            return false;
        if (other.getSource() == null ^ this.getSource() == null)
            return false;
        if (other.getSource() != null && other.getSource().equals(this.getSource()) == false)
            return false;
        if (other.getTarget() == null ^ this.getTarget() == null)
            return false;
        if (other.getTarget() != null && other.getTarget().equals(this.getTarget()) == false)
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

        hashCode = prime * hashCode + ((getAction() == null) ? 0 : getAction().hashCode());
        hashCode = prime * hashCode + ((getActor() == null) ? 0 : getActor().hashCode());
        hashCode = prime * hashCode + ((getContext() == null) ? 0 : getContext().hashCode());
        hashCode = prime * hashCode + ((getSource() == null) ? 0 : getSource().hashCode());
        hashCode = prime * hashCode + ((getTarget() == null) ? 0 : getTarget().hashCode());
        hashCode = prime * hashCode + ((getTimestamp() == null) ? 0 : getTimestamp().hashCode());
        hashCode = prime * hashCode + ((getValue() == null) ? 0 : getValue().hashCode());
        return hashCode;
    }

    @Override
    public InputItem clone() {
        try {
            return (InputItem) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
