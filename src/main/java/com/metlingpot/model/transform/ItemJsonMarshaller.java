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
 * ItemMarshaller
 */
public class ItemJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(Item item, StructuredJsonGenerator jsonGenerator) {

        if (item == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            if (item.getAction() != null) {
                jsonGenerator.writeFieldName("action");
                TypedValueJsonMarshaller.getInstance().marshall(item.getAction(), jsonGenerator);
            }
            if (item.getActor() != null) {
                jsonGenerator.writeFieldName("actor");
                TypedValueJsonMarshaller.getInstance().marshall(item.getActor(), jsonGenerator);
            }
            if (item.getContext() != null) {
                jsonGenerator.writeFieldName("context");
                TypedValueJsonMarshaller.getInstance().marshall(item.getContext(), jsonGenerator);
            }
            if (item.getSource() != null) {
                jsonGenerator.writeFieldName("source").writeValue(item.getSource());
            }
            if (item.getTarget() != null) {
                jsonGenerator.writeFieldName("target");
                TypedValueJsonMarshaller.getInstance().marshall(item.getTarget(), jsonGenerator);
            }
            if (item.getTimestamp() != null) {
                jsonGenerator.writeFieldName("timestamp").writeValue(item.getTimestamp());
            }
            if (item.getValue() != null) {
                jsonGenerator.writeFieldName("value").writeValue(item.getValue());
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static ItemJsonMarshaller instance;

    public static ItemJsonMarshaller getInstance() {
        if (instance == null)
            instance = new ItemJsonMarshaller();
        return instance;
    }

}
