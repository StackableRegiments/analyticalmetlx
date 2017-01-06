/**
 * apache2
 */
package com.metlingpot.model.transform;

import java.util.Map;
import java.util.List;

import com.amazonaws.SdkClientException;
import com.metlingpot.model.*;
import com.amazonaws.transform.Marshaller;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.StringUtils;
import com.amazonaws.util.IdempotentUtils;
import com.amazonaws.util.StringInputStream;
import com.amazonaws.protocol.json.*;

/**
 * SmartGroupsRequestMarshaller
 */
public class SmartGroupsRequestJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(SmartGroupsRequest smartGroupsRequest, StructuredJsonGenerator jsonGenerator) {

        if (smartGroupsRequest == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
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
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static SmartGroupsRequestJsonMarshaller instance;

    public static SmartGroupsRequestJsonMarshaller getInstance() {
        if (instance == null)
            instance = new SmartGroupsRequestJsonMarshaller();
        return instance;
    }

}
