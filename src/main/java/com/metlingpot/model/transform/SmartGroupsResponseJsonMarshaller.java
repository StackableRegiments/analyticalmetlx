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
 * SmartGroupsResponseMarshaller
 */
public class SmartGroupsResponseJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(SmartGroupsResponse smartGroupsResponse, StructuredJsonGenerator jsonGenerator) {

        if (smartGroupsResponse == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            java.util.List<GroupSet> groupSetsList = smartGroupsResponse.getGroupSets();
            if (groupSetsList != null) {
                jsonGenerator.writeFieldName("groupSets");
                jsonGenerator.writeStartArray();
                for (GroupSet groupSetsListValue : groupSetsList) {
                    if (groupSetsListValue != null) {

                        GroupSetJsonMarshaller.getInstance().marshall(groupSetsListValue, jsonGenerator);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            java.util.List<String> membersList = smartGroupsResponse.getMembers();
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

    private static SmartGroupsResponseJsonMarshaller instance;

    public static SmartGroupsResponseJsonMarshaller getInstance() {
        if (instance == null)
            instance = new SmartGroupsResponseJsonMarshaller();
        return instance;
    }

}
