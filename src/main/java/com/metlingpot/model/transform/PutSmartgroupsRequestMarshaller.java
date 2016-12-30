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
 * PutSmartgroupsRequest Marshaller
 */
public class PutSmartgroupsRequestMarshaller implements Marshaller<Request<PutSmartgroupsRequest>, PutSmartgroupsRequest> {

    private final SdkJsonMarshallerFactory protocolFactory;

    public PutSmartgroupsRequestMarshaller(SdkJsonMarshallerFactory protocolFactory) {
        this.protocolFactory = protocolFactory;
    }

    public Request<PutSmartgroupsRequest> marshall(PutSmartgroupsRequest putSmartgroupsRequest) {

        if (putSmartgroupsRequest == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        Request<PutSmartgroupsRequest> request = new DefaultRequest<PutSmartgroupsRequest>("MetlingPotInputItem");

        request.setHttpMethod(HttpMethodName.PUT);

        String uriResourcePath = "/future/smartgroups";

        request.setResourcePath(uriResourcePath);

        try {
            final StructuredJsonGenerator jsonGenerator = protocolFactory.createGenerator();

            SmartGroupsRequest smartGroupsRequest = putSmartgroupsRequest.getSmartGroupsRequest();
            if (smartGroupsRequest != null) {
                jsonGenerator.writeStartObject();
                if (smartGroupsRequest.getGroupCount() != null) {
                    jsonGenerator.writeFieldName("groupCount").writeValue(smartGroupsRequest.getGroupCount());
                }

                java.util.List<String> membersList = smartGroupsRequest.getMembers();
                if (membersList != null) {
                    jsonGenerator.writeFieldName("members");
                    jsonGenerator.writeStartArray();
                    for (String membersListValue : membersList) {
                        if (membersListValue != null) {
                            jsonGenerator.writeValue(membersListValue);
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
