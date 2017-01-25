/**
 * apache2
 */
package com.metlingpot;

import java.net.*;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.logging.*;

import com.amazonaws.*;
import com.amazonaws.opensdk.*;
import com.amazonaws.opensdk.model.*;
import com.amazonaws.opensdk.protect.model.transform.*;
import com.amazonaws.auth.*;
import com.amazonaws.handlers.*;
import com.amazonaws.http.*;
import com.amazonaws.internal.*;
import com.amazonaws.metrics.*;
import com.amazonaws.regions.*;
import com.amazonaws.transform.*;
import com.amazonaws.util.*;
import com.amazonaws.protocol.json.*;
import com.amazonaws.util.AWSRequestMetrics.Field;
import com.amazonaws.annotation.ThreadSafe;
import com.amazonaws.client.AwsSyncClientParams;
import com.amazonaws.client.ClientHandler;
import com.amazonaws.client.ClientHandler;
import com.amazonaws.client.ClientHandlerParams;
import com.amazonaws.client.ClientExecutionParams;
import com.amazonaws.opensdk.protect.client.SdkClientHandler;
import com.amazonaws.SdkBaseException;

import com.metlingpot.model.*;
import com.metlingpot.model.transform.*;

/**
 * Client for accessing MetlingPotInputItem. All service calls made using this client are blocking, and will not return
 * until the service call completes.
 * <p>
 * 
 */
@ThreadSafe
public class MetlingPotInputItemClient implements MetlingPotInputItem {

    private final ClientHandler clientHandler;

    private final com.amazonaws.opensdk.protect.protocol.ApiGatewayProtocolFactoryImpl protocolFactory = new com.amazonaws.opensdk.protect.protocol.ApiGatewayProtocolFactoryImpl(
            new JsonClientMetadata()
                    .withProtocolVersion("1.1")
                    .withSupportsCbor(false)
                    .withSupportsIon(false)
                    .withContentTypeOverride("application/json")
                    .addErrorMetadata(
                            new JsonErrorShapeMetadata().withErrorCode("InternalServerErrorException").withModeledClass(
                                    com.metlingpot.model.InternalServerErrorException.class))
                    .withBaseServiceExceptionClass(com.metlingpot.model.MetlingPotInputItemException.class));

    /**
     * Constructs a new client to invoke service methods on MetlingPotInputItem using the specified parameters.
     *
     * <p>
     * All service calls made using this new client object are blocking, and will not return until the service call
     * completes.
     *
     * @param clientParams
     *        Object providing client parameters.
     */
    MetlingPotInputItemClient(AwsSyncClientParams clientParams) {
        this.clientHandler = new SdkClientHandler(new ClientHandlerParams().withClientParams(clientParams));
    }

    /**
     * @param putInputItemRequest
     * @return Result of the PutInputItem operation returned by the service.
     * @throws InternalServerErrorException
     * @sample MetlingPotInputItem.PutInputItem
     */
    @Override
    public PutInputItemResult putInputItem(PutInputItemRequest putInputItemRequest) {
        HttpResponseHandler<PutInputItemResult> responseHandler = protocolFactory.createResponseHandler(new JsonOperationMetadata().withPayloadJson(true)
                .withHasStreamingSuccessResponse(false), new PutInputItemResultJsonUnmarshaller());

        HttpResponseHandler<SdkBaseException> errorResponseHandler = createErrorResponseHandler(new JsonErrorShapeMetadata().withModeledClass(
                InternalServerErrorException.class).withHttpStatusCode(500));

        return clientHandler.execute(new ClientExecutionParams<PutInputItemRequest, PutInputItemResult>()
                .withMarshaller(new PutInputItemRequestMarshaller(protocolFactory)).withResponseHandler(responseHandler)
                .withErrorResponseHandler(errorResponseHandler).withInput(putInputItemRequest));
    }

    /**
     * @param putSearchRequest
     * @return Result of the PutSearch operation returned by the service.
     * @throws InternalServerErrorException
     * @sample MetlingPotInputItem.PutSearch
     */
    @Override
    public PutSearchResult putSearch(PutSearchRequest putSearchRequest) {
        HttpResponseHandler<PutSearchResult> responseHandler = protocolFactory.createResponseHandler(new JsonOperationMetadata().withPayloadJson(true)
                .withHasStreamingSuccessResponse(false), new PutSearchResultJsonUnmarshaller());

        HttpResponseHandler<SdkBaseException> errorResponseHandler = createErrorResponseHandler(new JsonErrorShapeMetadata().withModeledClass(
                InternalServerErrorException.class).withHttpStatusCode(500));

        return clientHandler.execute(new ClientExecutionParams<PutSearchRequest, PutSearchResult>()
                .withMarshaller(new PutSearchRequestMarshaller(protocolFactory)).withResponseHandler(responseHandler)
                .withErrorResponseHandler(errorResponseHandler).withInput(putSearchRequest));
    }

    /**
     * @param putSmartgroupsRequest
     * @return Result of the PutSmartgroups operation returned by the service.
     * @throws InternalServerErrorException
     * @sample MetlingPotInputItem.PutSmartgroups
     */
    @Override
    public PutSmartgroupsResult putSmartgroups(PutSmartgroupsRequest putSmartgroupsRequest) {
        HttpResponseHandler<PutSmartgroupsResult> responseHandler = protocolFactory.createResponseHandler(new JsonOperationMetadata().withPayloadJson(true)
                .withHasStreamingSuccessResponse(false), new PutSmartgroupsResultJsonUnmarshaller());

        HttpResponseHandler<SdkBaseException> errorResponseHandler = createErrorResponseHandler(new JsonErrorShapeMetadata().withModeledClass(
                InternalServerErrorException.class).withHttpStatusCode(500));

        return clientHandler.execute(new ClientExecutionParams<PutSmartgroupsRequest, PutSmartgroupsResult>()
                .withMarshaller(new PutSmartgroupsRequestMarshaller(protocolFactory)).withResponseHandler(responseHandler)
                .withErrorResponseHandler(errorResponseHandler).withInput(putSmartgroupsRequest));
    }

    @Override
    public RawResult execute(RawRequest request) {
        return execute(request, (r, c) -> {
        });
    }

    @Override
    public RawResult execute(RawRequest request, ResultContentConsumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }

        HttpResponseHandler<RawResult> responseHandler = protocolFactory.createResponseHandler(new JsonOperationMetadata().withPayloadJson(false)
                .withHasStreamingSuccessResponse(true), new RawResultUnmarshaller(consumer));

        HttpResponseHandler<SdkBaseException> errorResponseHandler = createErrorResponseHandler();

        return clientHandler.execute(new ClientExecutionParams<RawRequest, RawResult>().withMarshaller(new RawRequestMarshaller(protocolFactory))
                .withResponseHandler(responseHandler).withErrorResponseHandler(errorResponseHandler).withInput(request));
    }

    /**
     * Create the error response handler for the operation.
     * 
     * @param errorShapeMetadata
     *        Error metadata for the given operation
     * @return Configured error response handler to pass to HTTP layer
     */
    private HttpResponseHandler<SdkBaseException> createErrorResponseHandler(JsonErrorShapeMetadata... errorShapeMetadata) {
        return protocolFactory.createErrorResponseHandler(new JsonErrorResponseMetadata().withErrorShapes(Arrays.asList(errorShapeMetadata)));
    }

    @Override
    public void shutdown() {
        clientHandler.shutdown();
    }

}
