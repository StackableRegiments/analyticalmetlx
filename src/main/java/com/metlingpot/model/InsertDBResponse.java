/**
 * apache2
 */
package com.metlingpot.model;

import java.io.Serializable;

/**
 * 
 */
public class InsertDBResponse implements Serializable, Cloneable {

    private Double affectedRows;

    private Double changedRows;

    private Double fieldCount;

    private Double insertId;

    private String message;

    private Boolean protocol41;

    private Double serverStatus;

    private Double warningCount;

    /**
     * @param affectedRows
     */

    public void setAffectedRows(Double affectedRows) {
        this.affectedRows = affectedRows;
    }

    /**
     * @return
     */

    public Double getAffectedRows() {
        return this.affectedRows;
    }

    /**
     * @param affectedRows
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertDBResponse affectedRows(Double affectedRows) {
        setAffectedRows(affectedRows);
        return this;
    }

    /**
     * @param changedRows
     */

    public void setChangedRows(Double changedRows) {
        this.changedRows = changedRows;
    }

    /**
     * @return
     */

    public Double getChangedRows() {
        return this.changedRows;
    }

    /**
     * @param changedRows
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertDBResponse changedRows(Double changedRows) {
        setChangedRows(changedRows);
        return this;
    }

    /**
     * @param fieldCount
     */

    public void setFieldCount(Double fieldCount) {
        this.fieldCount = fieldCount;
    }

    /**
     * @return
     */

    public Double getFieldCount() {
        return this.fieldCount;
    }

    /**
     * @param fieldCount
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertDBResponse fieldCount(Double fieldCount) {
        setFieldCount(fieldCount);
        return this;
    }

    /**
     * @param insertId
     */

    public void setInsertId(Double insertId) {
        this.insertId = insertId;
    }

    /**
     * @return
     */

    public Double getInsertId() {
        return this.insertId;
    }

    /**
     * @param insertId
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertDBResponse insertId(Double insertId) {
        setInsertId(insertId);
        return this;
    }

    /**
     * @param message
     */

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * @return
     */

    public String getMessage() {
        return this.message;
    }

    /**
     * @param message
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertDBResponse message(String message) {
        setMessage(message);
        return this;
    }

    /**
     * @param protocol41
     */

    public void setProtocol41(Boolean protocol41) {
        this.protocol41 = protocol41;
    }

    /**
     * @return
     */

    public Boolean getProtocol41() {
        return this.protocol41;
    }

    /**
     * @param protocol41
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertDBResponse protocol41(Boolean protocol41) {
        setProtocol41(protocol41);
        return this;
    }

    /**
     * @return
     */

    public Boolean isProtocol41() {
        return this.protocol41;
    }

    /**
     * @param serverStatus
     */

    public void setServerStatus(Double serverStatus) {
        this.serverStatus = serverStatus;
    }

    /**
     * @return
     */

    public Double getServerStatus() {
        return this.serverStatus;
    }

    /**
     * @param serverStatus
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertDBResponse serverStatus(Double serverStatus) {
        setServerStatus(serverStatus);
        return this;
    }

    /**
     * @param warningCount
     */

    public void setWarningCount(Double warningCount) {
        this.warningCount = warningCount;
    }

    /**
     * @return
     */

    public Double getWarningCount() {
        return this.warningCount;
    }

    /**
     * @param warningCount
     * @return Returns a reference to this object so that method calls can be chained together.
     */

    public InsertDBResponse warningCount(Double warningCount) {
        setWarningCount(warningCount);
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
        if (getAffectedRows() != null)
            sb.append("AffectedRows: ").append(getAffectedRows()).append(",");
        if (getChangedRows() != null)
            sb.append("ChangedRows: ").append(getChangedRows()).append(",");
        if (getFieldCount() != null)
            sb.append("FieldCount: ").append(getFieldCount()).append(",");
        if (getInsertId() != null)
            sb.append("InsertId: ").append(getInsertId()).append(",");
        if (getMessage() != null)
            sb.append("Message: ").append(getMessage()).append(",");
        if (getProtocol41() != null)
            sb.append("Protocol41: ").append(getProtocol41()).append(",");
        if (getServerStatus() != null)
            sb.append("ServerStatus: ").append(getServerStatus()).append(",");
        if (getWarningCount() != null)
            sb.append("WarningCount: ").append(getWarningCount());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof InsertDBResponse == false)
            return false;
        InsertDBResponse other = (InsertDBResponse) obj;
        if (other.getAffectedRows() == null ^ this.getAffectedRows() == null)
            return false;
        if (other.getAffectedRows() != null && other.getAffectedRows().equals(this.getAffectedRows()) == false)
            return false;
        if (other.getChangedRows() == null ^ this.getChangedRows() == null)
            return false;
        if (other.getChangedRows() != null && other.getChangedRows().equals(this.getChangedRows()) == false)
            return false;
        if (other.getFieldCount() == null ^ this.getFieldCount() == null)
            return false;
        if (other.getFieldCount() != null && other.getFieldCount().equals(this.getFieldCount()) == false)
            return false;
        if (other.getInsertId() == null ^ this.getInsertId() == null)
            return false;
        if (other.getInsertId() != null && other.getInsertId().equals(this.getInsertId()) == false)
            return false;
        if (other.getMessage() == null ^ this.getMessage() == null)
            return false;
        if (other.getMessage() != null && other.getMessage().equals(this.getMessage()) == false)
            return false;
        if (other.getProtocol41() == null ^ this.getProtocol41() == null)
            return false;
        if (other.getProtocol41() != null && other.getProtocol41().equals(this.getProtocol41()) == false)
            return false;
        if (other.getServerStatus() == null ^ this.getServerStatus() == null)
            return false;
        if (other.getServerStatus() != null && other.getServerStatus().equals(this.getServerStatus()) == false)
            return false;
        if (other.getWarningCount() == null ^ this.getWarningCount() == null)
            return false;
        if (other.getWarningCount() != null && other.getWarningCount().equals(this.getWarningCount()) == false)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode + ((getAffectedRows() == null) ? 0 : getAffectedRows().hashCode());
        hashCode = prime * hashCode + ((getChangedRows() == null) ? 0 : getChangedRows().hashCode());
        hashCode = prime * hashCode + ((getFieldCount() == null) ? 0 : getFieldCount().hashCode());
        hashCode = prime * hashCode + ((getInsertId() == null) ? 0 : getInsertId().hashCode());
        hashCode = prime * hashCode + ((getMessage() == null) ? 0 : getMessage().hashCode());
        hashCode = prime * hashCode + ((getProtocol41() == null) ? 0 : getProtocol41().hashCode());
        hashCode = prime * hashCode + ((getServerStatus() == null) ? 0 : getServerStatus().hashCode());
        hashCode = prime * hashCode + ((getWarningCount() == null) ? 0 : getWarningCount().hashCode());
        return hashCode;
    }

    @Override
    public InsertDBResponse clone() {
        try {
            return (InsertDBResponse) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Got a CloneNotSupportedException from Object.clone() " + "even though we're Cloneable!", e);
        }
    }
}
