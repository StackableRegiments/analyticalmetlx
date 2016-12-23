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
import com.metlingpot.model.InputItemsResponseItemDbResponse;
import com.metlingpot.model.InputItemsResponseItemItem;

public class InputItemsResponseItem {
    @com.google.gson.annotations.SerializedName("index")
    private BigDecimal index = null;
    @com.google.gson.annotations.SerializedName("item")
    private InputItemsResponseItemItem item = null;
    @com.google.gson.annotations.SerializedName("dbResponse")
    private InputItemsResponseItemDbResponse dbResponse = null;

    /**
     * Gets index
     *
     * @return index
     **/
    public BigDecimal getIndex() {
        return index;
    }

    /**
     * Sets the value of index.
     *
     * @param index the new value
     */
    public void setIndex(BigDecimal index) {
        this.index = index;
    }

    /**
     * Gets item
     *
     * @return item
     **/
    public InputItemsResponseItemItem getItem() {
        return item;
    }

    /**
     * Sets the value of item.
     *
     * @param item the new value
     */
    public void setItem(InputItemsResponseItemItem item) {
        this.item = item;
    }

    /**
     * Gets dbResponse
     *
     * @return dbResponse
     **/
    public InputItemsResponseItemDbResponse getDbResponse() {
        return dbResponse;
    }

    /**
     * Sets the value of dbResponse.
     *
     * @param dbResponse the new value
     */
    public void setDbResponse(InputItemsResponseItemDbResponse dbResponse) {
        this.dbResponse = dbResponse;
    }

}
