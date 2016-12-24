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
import com.metlingpot.model.SmartGroupsResponseGroupSetsItemGroupsItem;

public class SmartGroupsResponseGroupSetsItem {
    @com.google.gson.annotations.SerializedName("name")
    private String name = null;
    @com.google.gson.annotations.SerializedName("groups")
    private List<SmartGroupsResponseGroupSetsItemGroupsItem> groups = null;

    /**
     * Gets name
     *
     * @return name
     **/
    public String getName() {
        return name;
    }

    /**
     * Sets the value of name.
     *
     * @param name the new value
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets groups
     *
     * @return groups
     **/
    public List<SmartGroupsResponseGroupSetsItemGroupsItem> getGroups() {
        return groups;
    }

    /**
     * Sets the value of groups.
     *
     * @param groups the new value
     */
    public void setGroups(List<SmartGroupsResponseGroupSetsItemGroupsItem> groups) {
        this.groups = groups;
    }

}
