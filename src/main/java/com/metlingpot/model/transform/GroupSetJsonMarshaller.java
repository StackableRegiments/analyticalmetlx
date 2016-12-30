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
 * GroupSetMarshaller
 */
public class GroupSetJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(GroupSet groupSet, StructuredJsonGenerator jsonGenerator) {

        if (groupSet == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            java.util.List<Group> groupsList = groupSet.getGroups();
            if (groupsList != null) {
                jsonGenerator.writeFieldName("groups");
                jsonGenerator.writeStartArray();
                for (Group groupsListValue : groupsList) {
                    if (groupsListValue != null) {

                        GroupJsonMarshaller.getInstance().marshall(groupsListValue, jsonGenerator);
                    }
                }
                jsonGenerator.writeEndArray();
            }
            if (groupSet.getName() != null) {
                jsonGenerator.writeFieldName("name").writeValue(groupSet.getName());
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static GroupSetJsonMarshaller instance;

    public static GroupSetJsonMarshaller getInstance() {
        if (instance == null)
            instance = new GroupSetJsonMarshaller();
        return instance;
    }

}
