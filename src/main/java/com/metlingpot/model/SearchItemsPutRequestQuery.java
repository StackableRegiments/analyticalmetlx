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

import java.util.*;

public class SearchItemsPutRequestQuery {
    @com.google.gson.annotations.SerializedName("source")
    private List<String> source = null;
    @com.google.gson.annotations.SerializedName("actortype")
    private List<String> actortype = null;
    @com.google.gson.annotations.SerializedName("actorname")
    private List<String> actorname = null;
    @com.google.gson.annotations.SerializedName("actiontype")
    private List<String> actiontype = null;
    @com.google.gson.annotations.SerializedName("actionname")
    private List<String> actionname = null;
    @com.google.gson.annotations.SerializedName("targettype")
    private List<String> targettype = null;
    @com.google.gson.annotations.SerializedName("targetname")
    private List<String> targetname = null;
    @com.google.gson.annotations.SerializedName("contexttype")
    private List<String> contexttype = null;
    @com.google.gson.annotations.SerializedName("contextname")
    private List<String> contextname = null;

    /**
     * Gets source
     *
     * @return source
     **/
    public List<String> getSource() {
        return source;
    }

    /**
     * Sets the value of source.
     *
     * @param source the new value
     */
    public void setSource(List<String> source) {
        this.source = source;
    }

    /**
     * Gets actortype
     *
     * @return actortype
     **/
    public List<String> getActortype() {
        return actortype;
    }

    /**
     * Sets the value of actortype.
     *
     * @param actortype the new value
     */
    public void setActortype(List<String> actortype) {
        this.actortype = actortype;
    }

    /**
     * Gets actorname
     *
     * @return actorname
     **/
    public List<String> getActorname() {
        return actorname;
    }

    /**
     * Sets the value of actorname.
     *
     * @param actorname the new value
     */
    public void setActorname(List<String> actorname) {
        this.actorname = actorname;
    }

    /**
     * Gets actiontype
     *
     * @return actiontype
     **/
    public List<String> getActiontype() {
        return actiontype;
    }

    /**
     * Sets the value of actiontype.
     *
     * @param actiontype the new value
     */
    public void setActiontype(List<String> actiontype) {
        this.actiontype = actiontype;
    }

    /**
     * Gets actionname
     *
     * @return actionname
     **/
    public List<String> getActionname() {
        return actionname;
    }

    /**
     * Sets the value of actionname.
     *
     * @param actionname the new value
     */
    public void setActionname(List<String> actionname) {
        this.actionname = actionname;
    }

    /**
     * Gets targettype
     *
     * @return targettype
     **/
    public List<String> getTargettype() {
        return targettype;
    }

    /**
     * Sets the value of targettype.
     *
     * @param targettype the new value
     */
    public void setTargettype(List<String> targettype) {
        this.targettype = targettype;
    }

    /**
     * Gets targetname
     *
     * @return targetname
     **/
    public List<String> getTargetname() {
        return targetname;
    }

    /**
     * Sets the value of targetname.
     *
     * @param targetname the new value
     */
    public void setTargetname(List<String> targetname) {
        this.targetname = targetname;
    }

    /**
     * Gets contexttype
     *
     * @return contexttype
     **/
    public List<String> getContexttype() {
        return contexttype;
    }

    /**
     * Sets the value of contexttype.
     *
     * @param contexttype the new value
     */
    public void setContexttype(List<String> contexttype) {
        this.contexttype = contexttype;
    }

    /**
     * Gets contextname
     *
     * @return contextname
     **/
    public List<String> getContextname() {
        return contextname;
    }

    /**
     * Sets the value of contextname.
     *
     * @param contextname the new value
     */
    public void setContextname(List<String> contextname) {
        this.contextname = contextname;
    }

}
