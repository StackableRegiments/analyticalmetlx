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
 * SearchItemsPutRequestMarshaller
 */
public class SearchItemsPutRequestJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(SearchItemsPutRequest searchItemsPutRequest, StructuredJsonGenerator jsonGenerator) {

        if (searchItemsPutRequest == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            if (searchItemsPutRequest.getAfter() != null) {
                jsonGenerator.writeFieldName("after").writeValue(searchItemsPutRequest.getAfter());
            }
            if (searchItemsPutRequest.getBefore() != null) {
                jsonGenerator.writeFieldName("before").writeValue(searchItemsPutRequest.getBefore());
            }
            if (searchItemsPutRequest.getQuery() != null) {
                jsonGenerator.writeFieldName("query");
                QueryJsonMarshaller.getInstance().marshall(searchItemsPutRequest.getQuery(), jsonGenerator);
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static SearchItemsPutRequestJsonMarshaller instance;

    public static SearchItemsPutRequestJsonMarshaller getInstance() {
        if (instance == null)
            instance = new SearchItemsPutRequestJsonMarshaller();
        return instance;
    }

}
