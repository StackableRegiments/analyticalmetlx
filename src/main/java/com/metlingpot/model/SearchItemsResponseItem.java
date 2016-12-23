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
import com.metlingpot.model.SearchItemsResponseItemItemsItem;

public class SearchItemsResponseItem {
    @com.google.gson.annotations.SerializedName("query")
    private String query = null;
    @com.google.gson.annotations.SerializedName("count")
    private BigDecimal count = null;
    @com.google.gson.annotations.SerializedName("items")
    private List<SearchItemsResponseItemItemsItem> items = null;

    /**
     * Gets query
     *
     * @return query
     **/
    public String getQuery() {
        return query;
    }

    /**
     * Sets the value of query.
     *
     * @param query the new value
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * Gets count
     *
     * @return count
     **/
    public BigDecimal getCount() {
        return count;
    }

    /**
     * Sets the value of count.
     *
     * @param count the new value
     */
    public void setCount(BigDecimal count) {
        this.count = count;
    }

    /**
     * Gets items
     *
     * @return items
     **/
    public List<SearchItemsResponseItemItemsItem> getItems() {
        return items;
    }

    /**
     * Sets the value of items.
     *
     * @param items the new value
     */
    public void setItems(List<SearchItemsResponseItemItemsItem> items) {
        this.items = items;
    }

}
