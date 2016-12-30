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
 * GroupMarshaller
 */
public class GroupJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(Group group, StructuredJsonGenerator jsonGenerator) {

        if (group == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            java.util.List<String> membersList = group.getMembers();
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
            if (group.getName() != null) {
                jsonGenerator.writeFieldName("name").writeValue(group.getName());
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static GroupJsonMarshaller instance;

    public static GroupJsonMarshaller getInstance() {
        if (instance == null)
            instance = new GroupJsonMarshaller();
        return instance;
    }

}
