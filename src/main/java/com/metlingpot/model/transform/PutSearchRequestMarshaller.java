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
 * PutSearchRequest Marshaller
 */
public class PutSearchRequestMarshaller implements Marshaller<Request<PutSearchRequest>, PutSearchRequest> {

    private final SdkJsonMarshallerFactory protocolFactory;

    public PutSearchRequestMarshaller(SdkJsonMarshallerFactory protocolFactory) {
        this.protocolFactory = protocolFactory;
    }

    public Request<PutSearchRequest> marshall(PutSearchRequest putSearchRequest) {

        if (putSearchRequest == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        Request<PutSearchRequest> request = new DefaultRequest<PutSearchRequest>("MetlingPotInputItem");

        request.setHttpMethod(HttpMethodName.PUT);

        String uriResourcePath = "/future/search";

        request.setResourcePath(uriResourcePath);

        try {
            final StructuredJsonGenerator jsonGenerator = protocolFactory.createGenerator();

            SearchItemsPutRequest searchItemsPutRequest = putSearchRequest.getSearchItemsPutRequest();
            if (searchItemsPutRequest != null) {
                jsonGenerator.writeStartObject();
                if (searchItemsPutRequest.getAfter() != null) {
                    jsonGenerator.writeFieldName("after").writeValue(searchItemsPutRequest.getAfter());
                }
                if (searchItemsPutRequest.getBefore() != null) {
                    jsonGenerator.writeFieldName("before").writeValue(searchItemsPutRequest.getBefore());
                }
                if (searchItemsPutRequest.getQuery() != null) {
                    jsonGenerator.writeFieldName("query");
                    QueryJsonMarshaller.getInstance().marshall(searchItemsPutRequest.getQuery(), jsonGenerator);
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
