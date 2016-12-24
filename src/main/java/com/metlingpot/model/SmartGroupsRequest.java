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
import java.util.*;

public class SmartGroupsRequest {
    @com.google.gson.annotations.SerializedName("groupCount")
    private BigDecimal groupCount = null;
    @com.google.gson.annotations.SerializedName("members")
    private List<String> members = null;

    /**
     * Gets groupCount
     *
     * @return groupCount
     **/
    public BigDecimal getGroupCount() {
        return groupCount;
    }

    /**
     * Sets the value of groupCount.
     *
     * @param groupCount the new value
     */
    public void setGroupCount(BigDecimal groupCount) {
        this.groupCount = groupCount;
    }

    /**
     * Gets members
     *
     * @return members
     **/
    public List<String> getMembers() {
        return members;
    }

    /**
     * Sets the value of members.
     *
     * @param members the new value
     */
    public void setMembers(List<String> members) {
        this.members = members;
    }

}
