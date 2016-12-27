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

public class SearchItemsResponseItemItemsItem {
    @com.google.gson.annotations.SerializedName("id")
    private BigDecimal id = null;
    @com.google.gson.annotations.SerializedName("source")
    private String source = null;
    @com.google.gson.annotations.SerializedName("timestamp")
    private BigDecimal timestamp = null;
    @com.google.gson.annotations.SerializedName("actortype")
    private String actortype = null;
    @com.google.gson.annotations.SerializedName("actorname")
    private String actorname = null;
    @com.google.gson.annotations.SerializedName("actiontype")
    private String actiontype = null;
    @com.google.gson.annotations.SerializedName("actionname")
    private String actionname = null;
    @com.google.gson.annotations.SerializedName("targettype")
    private String targettype = null;
    @com.google.gson.annotations.SerializedName("targetname")
    private String targetname = null;
    @com.google.gson.annotations.SerializedName("contexttype")
    private String contexttype = null;
    @com.google.gson.annotations.SerializedName("contextname")
    private String contextname = null;
    @com.google.gson.annotations.SerializedName("value")
    private String value = null;

    /**
     * Gets id
     *
     * @return id
     **/
    public BigDecimal getId() {
        return id;
    }

    /**
     * Sets the value of id.
     *
     * @param id the new value
     */
    public void setId(BigDecimal id) {
        this.id = id;
    }

    /**
     * Gets source
     *
     * @return source
     **/
    public String getSource() {
        return source;
    }

    /**
     * Sets the value of source.
     *
     * @param source the new value
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Gets timestamp
     *
     * @return timestamp
     **/
    public BigDecimal getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the value of timestamp.
     *
     * @param timestamp the new value
     */
    public void setTimestamp(BigDecimal timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets actortype
     *
     * @return actortype
     **/
    public String getActortype() {
        return actortype;
    }

    /**
     * Sets the value of actortype.
     *
     * @param actortype the new value
     */
    public void setActortype(String actortype) {
        this.actortype = actortype;
    }

    /**
     * Gets actorname
     *
     * @return actorname
     **/
    public String getActorname() {
        return actorname;
    }

    /**
     * Sets the value of actorname.
     *
     * @param actorname the new value
     */
    public void setActorname(String actorname) {
        this.actorname = actorname;
    }

    /**
     * Gets actiontype
     *
     * @return actiontype
     **/
    public String getActiontype() {
        return actiontype;
    }

    /**
     * Sets the value of actiontype.
     *
     * @param actiontype the new value
     */
    public void setActiontype(String actiontype) {
        this.actiontype = actiontype;
    }

    /**
     * Gets actionname
     *
     * @return actionname
     **/
    public String getActionname() {
        return actionname;
    }

    /**
     * Sets the value of actionname.
     *
     * @param actionname the new value
     */
    public void setActionname(String actionname) {
        this.actionname = actionname;
    }

    /**
     * Gets targettype
     *
     * @return targettype
     **/
    public String getTargettype() {
        return targettype;
    }

    /**
     * Sets the value of targettype.
     *
     * @param targettype the new value
     */
    public void setTargettype(String targettype) {
        this.targettype = targettype;
    }

    /**
     * Gets targetname
     *
     * @return targetname
     **/
    public String getTargetname() {
        return targetname;
    }

    /**
     * Sets the value of targetname.
     *
     * @param targetname the new value
     */
    public void setTargetname(String targetname) {
        this.targetname = targetname;
    }

    /**
     * Gets contexttype
     *
     * @return contexttype
     **/
    public String getContexttype() {
        return contexttype;
    }

    /**
     * Sets the value of contexttype.
     *
     * @param contexttype the new value
     */
    public void setContexttype(String contexttype) {
        this.contexttype = contexttype;
    }

    /**
     * Gets contextname
     *
     * @return contextname
     **/
    public String getContextname() {
        return contextname;
    }

    /**
     * Sets the value of contextname.
     *
     * @param contextname the new value
     */
    public void setContextname(String contextname) {
        this.contextname = contextname;
    }

    /**
     * Gets value
     *
     * @return value
     **/
    public String getValue() {
        return value;
    }

    /**
     * Sets the value of value.
     *
     * @param value the new value
     */
    public void setValue(String value) {
        this.value = value;
    }

}
