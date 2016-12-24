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

package com.metlingpot;

import java.util.*;

import com.metlingpot.model.InputItemsPutRequest;
import com.metlingpot.model.Error;
import com.metlingpot.model.InputItemsResponse;
import com.metlingpot.model.SearchItemsResponse;
import com.metlingpot.model.SearchItemsPutRequest;
import com.metlingpot.model.SmartGroupsRequest;
import com.metlingpot.model.SmartGroupsResponse;


@com.amazonaws.mobileconnectors.apigateway.annotation.Service(endpoint = "https://jq6f5gsrq9.execute-api.us-east-1.amazonaws.com/future")
public interface MetlingPotInputItemClient {


    /**
     * A generic invoker to invoke any API Gateway endpoint.
     * @param request
     * @return ApiResponse
     */
    com.amazonaws.mobileconnectors.apigateway.ApiResponse execute(com.amazonaws.mobileconnectors.apigateway.ApiRequest request);
    
    /**
     * 
     * 
     * @param body 
     * @return InputItemsResponse
     */
    @com.amazonaws.mobileconnectors.apigateway.annotation.Operation(path = "/inputItem", method = "PUT")
    InputItemsResponse inputItemPut(
            InputItemsPutRequest body);
    
    /**
     * 
     * 
     * @param body 
     * @return SearchItemsResponse
     */
    @com.amazonaws.mobileconnectors.apigateway.annotation.Operation(path = "/search", method = "PUT")
    SearchItemsResponse searchPut(
            SearchItemsPutRequest body);
    
    /**
     * 
     * 
     * @param body 
     * @return SmartGroupsResponse
     */
    @com.amazonaws.mobileconnectors.apigateway.annotation.Operation(path = "/smartgroups", method = "PUT")
    SmartGroupsResponse smartgroupsPut(
            SmartGroupsRequest body);
    
}

