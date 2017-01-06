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
 * InputItemsPutRequestMarshaller
 */
public class InputItemsPutRequestJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(InputItemsPutRequest inputItemsPutRequest, StructuredJsonGenerator jsonGenerator) {

        if (inputItemsPutRequest == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            java.util.List<InputItem> itemsList = inputItemsPutRequest.getItems();
            if (itemsList != null) {
                jsonGenerator.writeFieldName("items");
                jsonGenerator.writeStartArray();
                for (InputItem itemsListValue : itemsList) {
                    if (itemsListValue != null) {

                        InputItemJsonMarshaller.getInstance().marshall(itemsListValue, jsonGenerator);
                    }
                }
                jsonGenerator.writeEndArray();
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static InputItemsPutRequestJsonMarshaller instance;

    public static InputItemsPutRequestJsonMarshaller getInstance() {
        if (instance == null)
            instance = new InputItemsPutRequestJsonMarshaller();
        return instance;
    }

}
