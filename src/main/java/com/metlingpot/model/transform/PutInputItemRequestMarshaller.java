/**
 * apache2
 */
package com.metlingpot.model.transform;

import static com.amazonaws.util.StringUtils.UTF8;
import static com.amazonaws.util.StringUtils.COMMA_SEPARATOR;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;

import com.amazonaws.SdkClientException;
import com.amazonaws.Request;
import com.amazonaws.DefaultRequest;
import com.amazonaws.http.HttpMethodName;
import com.metlingpot.model.*;
import com.amazonaws.transform.Marshaller;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.StringUtils;
import com.amazonaws.util.IdempotentUtils;
import com.amazonaws.util.StringInputStream;
import com.amazonaws.util.SdkHttpUtils;
import com.amazonaws.protocol.json.*;

/**
 * PutInputItemRequest Marshaller
 */
public class PutInputItemRequestMarshaller implements Marshaller<Request<PutInputItemRequest>, PutInputItemRequest> {

    private final SdkJsonMarshallerFactory protocolFactory;

    public PutInputItemRequestMarshaller(SdkJsonMarshallerFactory protocolFactory) {
        this.protocolFactory = protocolFactory;
    }

    public Request<PutInputItemRequest> marshall(PutInputItemRequest putInputItemRequest) {

        if (putInputItemRequest == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        Request<PutInputItemRequest> request = new DefaultRequest<PutInputItemRequest>("MetlingPotInputItem");

        request.setHttpMethod(HttpMethodName.PUT);

        String uriResourcePath = "/future/inputItem";

        request.setResourcePath(uriResourcePath);

        try {
            final StructuredJsonGenerator jsonGenerator = protocolFactory.createGenerator();

            InputItemsPutRequest inputItemsPutRequest = putInputItemRequest.getInputItemsPutRequest();
            if (inputItemsPutRequest != null) {
                jsonGenerator.writeStartObject();

                java.util.List<InputItem> itemsList = inputItemsPutRequest.getItems();
                if (itemsList != null) {
                    jsonGenerator.writeFieldName("items");
                    jsonGenerator.writeStartArray();
                    for (InputItem itemsListValue : itemsList) {
                        if (itemsListValue != null) {

                            InputItemJsonMarshaller.getInstance().marshall(itemsListValue, jsonGenerator);
                        }
                    }
                    jsonGenerator.writeEndArray();
                }
                jsonGenerator.writeEndObject();
            }

            byte[] content = jsonGenerator.getBytes();
            request.setContent(new ByteArrayInputStream(content));
            request.addHeader("Content-Length", Integer.toString(content.length));
            if (!request.getHeaders().containsKey("Content-Type")) {
                request.addHeader("Content-Type", protocolFactory.getContentType());
            }
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }

        return request;
    }

}
