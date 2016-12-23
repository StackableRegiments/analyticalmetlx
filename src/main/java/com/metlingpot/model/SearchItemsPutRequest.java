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
import com.metlingpot.model.SearchItemsPutRequestQuery;

public class SearchItemsPutRequest {
    @com.google.gson.annotations.SerializedName("before")
    private BigDecimal before = null;
    @com.google.gson.annotations.SerializedName("after")
    private BigDecimal after = null;
    @com.google.gson.annotations.SerializedName("query")
    private SearchItemsPutRequestQuery query = null;

    /**
     * Gets before
     *
     * @return before
     **/
    public BigDecimal getBefore() {
        return before;
    }

    /**
     * Sets the value of before.
     *
     * @param before the new value
     */
    public void setBefore(BigDecimal before) {
        this.before = before;
    }

    /**
     * Gets after
     *
     * @return after
     **/
    public BigDecimal getAfter() {
        return after;
    }

    /**
     * Sets the value of after.
     *
     * @param after the new value
     */
    public void setAfter(BigDecimal after) {
        this.after = after;
    }

    /**
     * Gets query
     *
     * @return query
     **/
    public SearchItemsPutRequestQuery getQuery() {
        return query;
    }

    /**
     * Sets the value of query.
     *
     * @param query the new value
     */
    public void setQuery(SearchItemsPutRequestQuery query) {
        this.query = query;
    }

}
