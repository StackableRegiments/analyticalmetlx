/*
 * Copyright 2010-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.metlingpot.model;

import java.math.BigDecimal;

public class InputItemsResponseItemDbResponse {
    @com.google.gson.annotations.SerializedName("fieldCount")
    private BigDecimal fieldCount = null;
    @com.google.gson.annotations.SerializedName("affectedRows")
    private BigDecimal affectedRows = null;
    @com.google.gson.annotations.SerializedName("insertId")
    private BigDecimal insertId = null;
    @com.google.gson.annotations.SerializedName("serverStatus")
    private BigDecimal serverStatus = null;
    @com.google.gson.annotations.SerializedName("warningCount")
    private BigDecimal warningCount = null;
    @com.google.gson.annotations.SerializedName("message")
    private String message = null;
    @com.google.gson.annotations.SerializedName("protocol41")
    private Boolean protocol41 = null;
    @com.google.gson.annotations.SerializedName("changedRows")
    private BigDecimal changedRows = null;

    /**
     * Gets fieldCount
     *
     * @return fieldCount
     **/
    public BigDecimal getFieldCount() {
        return fieldCount;
    }

    /**
     * Sets the value of fieldCount.
     *
     * @param fieldCount the new value
     */
    public void setFieldCount(BigDecimal fieldCount) {
        this.fieldCount = fieldCount;
    }

    /**
     * Gets affectedRows
     *
     * @return affectedRows
     **/
    public BigDecimal getAffectedRows() {
        return affectedRows;
    }

    /**
     * Sets the value of affectedRows.
     *
     * @param affectedRows the new value
     */
    public void setAffectedRows(BigDecimal affectedRows) {
        this.affectedRows = affectedRows;
    }

    /**
     * Gets insertId
     *
     * @return insertId
     **/
    public BigDecimal getInsertId() {
        return insertId;
    }

    /**
     * Sets the value of insertId.
     *
     * @param insertId the new value
     */
    public void setInsertId(BigDecimal insertId) {
        this.insertId = insertId;
    }

    /**
     * Gets serverStatus
     *
     * @return serverStatus
     **/
    public BigDecimal getServerStatus() {
        return serverStatus;
    }

    /**
     * Sets the value of serverStatus.
     *
     * @param serverStatus the new value
     */
    public void setServerStatus(BigDecimal serverStatus) {
        this.serverStatus = serverStatus;
    }

    /**
     * Gets warningCount
     *
     * @return warningCount
     **/
    public BigDecimal getWarningCount() {
        return warningCount;
    }

    /**
     * Sets the value of warningCount.
     *
     * @param warningCount the new value
     */
    public void setWarningCount(BigDecimal warningCount) {
        this.warningCount = warningCount;
    }

    /**
     * Gets message
     *
     * @return message
     **/
    public String getMessage() {
        return message;
    }

    /**
     * Sets the value of message.
     *
     * @param message the new value
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets protocol41
     *
     * @return protocol41
     **/
    public Boolean getProtocol41() {
        return protocol41;
    }

    /**
     * Sets the value of protocol41.
     *
     * @param protocol41 the new value
     */
    public void setProtocol41(Boolean protocol41) {
        this.protocol41 = protocol41;
    }

    /**
     * Gets changedRows
     *
     * @return changedRows
     **/
    public BigDecimal getChangedRows() {
        return changedRows;
    }

    /**
     * Sets the value of changedRows.
     *
     * @param changedRows the new value
     */
    public void setChangedRows(BigDecimal changedRows) {
        this.changedRows = changedRows;
    }

}
