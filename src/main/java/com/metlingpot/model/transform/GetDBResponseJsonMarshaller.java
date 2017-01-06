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
 * GetDBResponseMarshaller
 */
public class GetDBResponseJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(GetDBResponse getDBResponse, StructuredJsonGenerator jsonGenerator) {

        if (getDBResponse == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            if (getDBResponse.getActionname() != null) {
                jsonGenerator.writeFieldName("actionname").writeValue(getDBResponse.getActionname());
            }
            if (getDBResponse.getActiontype() != null) {
                jsonGenerator.writeFieldName("actiontype").writeValue(getDBResponse.getActiontype());
            }
            if (getDBResponse.getActorname() != null) {
                jsonGenerator.writeFieldName("actorname").writeValue(getDBResponse.getActorname());
            }
            if (getDBResponse.getActortype() != null) {
                jsonGenerator.writeFieldName("actortype").writeValue(getDBResponse.getActortype());
            }
            if (getDBResponse.getContextname() != null) {
                jsonGenerator.writeFieldName("contextname").writeValue(getDBResponse.getContextname());
            }
            if (getDBResponse.getContexttype() != null) {
                jsonGenerator.writeFieldName("contexttype").writeValue(getDBResponse.getContexttype());
            }
            if (getDBResponse.getId() != null) {
                jsonGenerator.writeFieldName("id").writeValue(getDBResponse.getId());
            }
            if (getDBResponse.getSource() != null) {
                jsonGenerator.writeFieldName("source").writeValue(getDBResponse.getSource());
            }
            if (getDBResponse.getTargetname() != null) {
                jsonGenerator.writeFieldName("targetname").writeValue(getDBResponse.getTargetname());
            }
            if (getDBResponse.getTargettype() != null) {
                jsonGenerator.writeFieldName("targettype").writeValue(getDBResponse.getTargettype());
            }
            if (getDBResponse.getTimestamp() != null) {
                jsonGenerator.writeFieldName("timestamp").writeValue(getDBResponse.getTimestamp());
            }
            if (getDBResponse.getValue() != null) {
                jsonGenerator.writeFieldName("value").writeValue(getDBResponse.getValue());
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static GetDBResponseJsonMarshaller instance;

    public static GetDBResponseJsonMarshaller getInstance() {
        if (instance == null)
            instance = new GetDBResponseJsonMarshaller();
        return instance;
    }

}
