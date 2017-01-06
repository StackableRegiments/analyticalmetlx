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
 * SearchResponseMarshaller
 */
public class SearchResponseJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(SearchResponse searchResponse, StructuredJsonGenerator jsonGenerator) {

        if (searchResponse == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            if (searchResponse.getCount() != null) {
                jsonGenerator.writeFieldName("count").writeValue(searchResponse.getCount());
            }

            java.util.List<GetDBResponse> itemsList = searchResponse.getItems();
            if (itemsList != null) {
                jsonGenerator.writeFieldName("items");
                jsonGenerator.writeStartArray();
                for (GetDBResponse itemsListValue : itemsList) {
                    if (itemsListValue != null) {

                        GetDBResponseJsonMarshaller.getInstance().marshall(itemsListValue, jsonGenerator);
                    }
                }
                jsonGenerator.writeEndArray();
            }
            if (searchResponse.getQuery() != null) {
                jsonGenerator.writeFieldName("query").writeValue(searchResponse.getQuery());
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static SearchResponseJsonMarshaller instance;

    public static SearchResponseJsonMarshaller getInstance() {
        if (instance == null)
            instance = new SearchResponseJsonMarshaller();
        return instance;
    }

}
