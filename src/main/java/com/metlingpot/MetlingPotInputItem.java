/**
 * apache2
 */
package com.metlingpot;

import com.amazonaws.*;
import com.amazonaws.opensdk.*;
import com.amazonaws.opensdk.model.*;
import com.amazonaws.regions.*;

import com.metlingpot.model.*;

/**
 * Interface for accessing MetlingPotInputItem.
 */
public interface MetlingPotInputItem {

    /**
     * @param putInputItemRequest
     * @return Result of the PutInputItem operation returned by the service.
     * @throws InternalServerErrorException
     * @sample MetlingPotInputItem.PutInputItem
     */
    PutInputItemResult putInputItem(PutInputItemRequest putInputItemRequest);

    /**
     * @param putSearchRequest
     * @return Result of the PutSearch operation returned by the service.
     * @throws InternalServerErrorException
     * @sample MetlingPotInputItem.PutSearch
     */
    PutSearchResult putSearch(PutSearchRequest putSearchRequest);

    /**
     * @param putSmartgroupsRequest
     * @return Result of the PutSmartgroups operation returned by the service.
     * @throws InternalServerErrorException
     * @sample MetlingPotInputItem.PutSmartgroups
     */
    PutSmartgroupsResult putSmartgroups(PutSmartgroupsRequest putSmartgroupsRequest);

    /**
     * @return Create new instance of builder with all defaults set.
     */
    public static MetlingPotInputItemClientBuilder builder() {
        return new MetlingPotInputItemClientBuilder();
    }

    /**
     * Execute the given request.
     * <p>
     * Any content returned in the response will be discarded.
     *
     * @param request
     *        The request.
     *
     * @return The result of the request execution.
     */
    RawResult execute(RawRequest request);

    /**
     * Execute the given request.
     * <p>
     * The request content stream will be made available to the supplied {@link ResultContentConsumer}.
     *
     * @param request
     *        The request.
     * @param consumer
     *        The result content consumer.
     *
     * @return The result of the request execution.
     */
    RawResult execute(RawRequest request, ResultContentConsumer consumer);

    /**
     * Shuts down this client object, releasing any resources that might be held open. This is an optional method, and
     * callers are not expected to call it, but can if they want to explicitly release any open resources. Once a client
     * has been shutdown, it should not be used to make any more requests.
     */
    void shutdown();

}
