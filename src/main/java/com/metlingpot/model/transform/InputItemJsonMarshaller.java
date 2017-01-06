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
 * InputItemMarshaller
 */
public class InputItemJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(InputItem inputItem, StructuredJsonGenerator jsonGenerator) {

        if (inputItem == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            if (inputItem.getAction() != null) {
                jsonGenerator.writeFieldName("action");
                TypedValueJsonMarshaller.getInstance().marshall(inputItem.getAction(), jsonGenerator);
            }
            if (inputItem.getActor() != null) {
                jsonGenerator.writeFieldName("actor");
                TypedValueJsonMarshaller.getInstance().marshall(inputItem.getActor(), jsonGenerator);
            }
            if (inputItem.getContext() != null) {
                jsonGenerator.writeFieldName("context");
                TypedValueJsonMarshaller.getInstance().marshall(inputItem.getContext(), jsonGenerator);
            }
            if (inputItem.getSource() != null) {
                jsonGenerator.writeFieldName("source").writeValue(inputItem.getSource());
            }
            if (inputItem.getTarget() != null) {
                jsonGenerator.writeFieldName("target");
                TypedValueJsonMarshaller.getInstance().marshall(inputItem.getTarget(), jsonGenerator);
            }
            if (inputItem.getTimestamp() != null) {
                jsonGenerator.writeFieldName("timestamp").writeValue(inputItem.getTimestamp());
            }
            if (inputItem.getValue() != null) {
                jsonGenerator.writeFieldName("value").writeValue(inputItem.getValue());
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static InputItemJsonMarshaller instance;

    public static InputItemJsonMarshaller getInstance() {
        if (instance == null)
            instance = new InputItemJsonMarshaller();
        return instance;
    }

}
