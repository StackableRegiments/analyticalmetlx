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

import com.metlingpot.model.InputItemsPutRequestItemsItem;
import java.util.*;

public class InputItemsPutRequest {
    @com.google.gson.annotations.SerializedName("items")
    private List<InputItemsPutRequestItemsItem> items = null;

    /**
     * Gets items
     *
     * @return items
     **/
    public List<InputItemsPutRequestItemsItem> getItems() {
        return items;
    }

    /**
     * Sets the value of items.
     *
     * @param items the new value
     */
    public void setItems(List<InputItemsPutRequestItemsItem> items) {
        this.items = items;
    }

}
