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
 * QueryMarshaller
 */
public class QueryJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(Query query, StructuredJsonGenerator jsonGenerator) {

        if (query == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            java.util.List<String> actionnameList = query.getActionname();
            if (actionnameList != null) {
                jsonGenerator.writeFieldName("actionname");
                jsonGenerator.writeStartArray();
                for (String actionnameListValue : actionnameList) {
                    if (actionnameListValue != null) {
                        jsonGenerator.writeValue(actionnameListValue);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            java.util.List<String> actiontypeList = query.getActiontype();
            if (actiontypeList != null) {
                jsonGenerator.writeFieldName("actiontype");
                jsonGenerator.writeStartArray();
                for (String actiontypeListValue : actiontypeList) {
                    if (actiontypeListValue != null) {
                        jsonGenerator.writeValue(actiontypeListValue);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            java.util.List<String> actornameList = query.getActorname();
            if (actornameList != null) {
                jsonGenerator.writeFieldName("actorname");
                jsonGenerator.writeStartArray();
                for (String actornameListValue : actornameList) {
                    if (actornameListValue != null) {
                        jsonGenerator.writeValue(actornameListValue);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            java.util.List<String> actortypeList = query.getActortype();
            if (actortypeList != null) {
                jsonGenerator.writeFieldName("actortype");
                jsonGenerator.writeStartArray();
                for (String actortypeListValue : actortypeList) {
                    if (actortypeListValue != null) {
                        jsonGenerator.writeValue(actortypeListValue);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            java.util.List<String> contextnameList = query.getContextname();
            if (contextnameList != null) {
                jsonGenerator.writeFieldName("contextname");
                jsonGenerator.writeStartArray();
                for (String contextnameListValue : contextnameList) {
                    if (contextnameListValue != null) {
                        jsonGenerator.writeValue(contextnameListValue);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            java.util.List<String> contexttypeList = query.getContexttype();
            if (contexttypeList != null) {
                jsonGenerator.writeFieldName("contexttype");
                jsonGenerator.writeStartArray();
                for (String contexttypeListValue : contexttypeList) {
                    if (contexttypeListValue != null) {
                        jsonGenerator.writeValue(contexttypeListValue);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            java.util.List<String> sourceList = query.getSource();
            if (sourceList != null) {
                jsonGenerator.writeFieldName("source");
                jsonGenerator.writeStartArray();
                for (String sourceListValue : sourceList) {
                    if (sourceListValue != null) {
                        jsonGenerator.writeValue(sourceListValue);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            java.util.List<String> targetnameList = query.getTargetname();
            if (targetnameList != null) {
                jsonGenerator.writeFieldName("targetname");
                jsonGenerator.writeStartArray();
                for (String targetnameListValue : targetnameList) {
                    if (targetnameListValue != null) {
                        jsonGenerator.writeValue(targetnameListValue);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            java.util.List<String> targettypeList = query.getTargettype();
            if (targettypeList != null) {
                jsonGenerator.writeFieldName("targettype");
                jsonGenerator.writeStartArray();
                for (String targettypeListValue : targettypeList) {
                    if (targettypeListValue != null) {
                        jsonGenerator.writeValue(targettypeListValue);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static QueryJsonMarshaller instance;

    public static QueryJsonMarshaller getInstance() {
        if (instance == null)
            instance = new QueryJsonMarshaller();
        return instance;
    }

}
