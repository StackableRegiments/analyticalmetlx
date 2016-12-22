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
import com.metlingpot.model.InputItemsPutRequestItemsItemActor;

public class InputItemsPutRequestItemsItem {
    @com.google.gson.annotations.SerializedName("source")
    private String source = null;
    @com.google.gson.annotations.SerializedName("timestamp")
    private BigDecimal timestamp = null;
    @com.google.gson.annotations.SerializedName("actor")
    private InputItemsPutRequestItemsItemActor actor = null;
    @com.google.gson.annotations.SerializedName("action")
    private InputItemsPutRequestItemsItemActor action = null;
    @com.google.gson.annotations.SerializedName("target")
    private InputItemsPutRequestItemsItemActor target = null;
    @com.google.gson.annotations.SerializedName("context")
    private InputItemsPutRequestItemsItemActor context = null;
    @com.google.gson.annotations.SerializedName("value")
    private String value = null;

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
     * Gets actor
     *
     * @return actor
     **/
    public InputItemsPutRequestItemsItemActor getActor() {
        return actor;
    }

    /**
     * Sets the value of actor.
     *
     * @param actor the new value
     */
    public void setActor(InputItemsPutRequestItemsItemActor actor) {
        this.actor = actor;
    }

    /**
     * Gets action
     *
     * @return action
     **/
    public InputItemsPutRequestItemsItemActor getAction() {
        return action;
    }

    /**
     * Sets the value of action.
     *
     * @param action the new value
     */
    public void setAction(InputItemsPutRequestItemsItemActor action) {
        this.action = action;
    }

    /**
     * Gets target
     *
     * @return target
     **/
    public InputItemsPutRequestItemsItemActor getTarget() {
        return target;
    }

    /**
     * Sets the value of target.
     *
     * @param target the new value
     */
    public void setTarget(InputItemsPutRequestItemsItemActor target) {
        this.target = target;
    }

    /**
     * Gets context
     *
     * @return context
     **/
    public InputItemsPutRequestItemsItemActor getContext() {
        return context;
    }

    /**
     * Sets the value of context.
     *
     * @param context the new value
     */
    public void setContext(InputItemsPutRequestItemsItemActor context) {
        this.context = context;
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
