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
 * InsertResponseMarshaller
 */
public class InsertResponseJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(InsertResponse insertResponse, StructuredJsonGenerator jsonGenerator) {

        if (insertResponse == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            if (insertResponse.getDbResponse() != null) {
                jsonGenerator.writeFieldName("dbResponse");
                InsertDBResponseJsonMarshaller.getInstance().marshall(insertResponse.getDbResponse(), jsonGenerator);
            }
            if (insertResponse.getIndex() != null) {
                jsonGenerator.writeFieldName("index").writeValue(insertResponse.getIndex());
            }
            if (insertResponse.getItem() != null) {
                jsonGenerator.writeFieldName("item");
                ItemJsonMarshaller.getInstance().marshall(insertResponse.getItem(), jsonGenerator);
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static InsertResponseJsonMarshaller instance;

    public static InsertResponseJsonMarshaller getInstance() {
        if (instance == null)
            instance = new InsertResponseJsonMarshaller();
        return instance;
    }

}
