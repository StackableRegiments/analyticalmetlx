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
 * TypedValueMarshaller
 */
public class TypedValueJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(TypedValue typedValue, StructuredJsonGenerator jsonGenerator) {

        if (typedValue == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            if (typedValue.getName() != null) {
                jsonGenerator.writeFieldName("name").writeValue(typedValue.getName());
            }
            if (typedValue.getType() != null) {
                jsonGenerator.writeFieldName("type").writeValue(typedValue.getType());
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static TypedValueJsonMarshaller instance;

    public static TypedValueJsonMarshaller getInstance() {
        if (instance == null)
            instance = new TypedValueJsonMarshaller();
        return instance;
    }

}
