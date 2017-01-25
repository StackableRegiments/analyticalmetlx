/**
 * apache2
 */
package com.metlingpot.model.transform;

import java.util.Map;
import java.util.Map.Entry;
import java.math.*;
import java.nio.ByteBuffer;

import com.metlingpot.model.*;
import com.amazonaws.transform.SimpleTypeJsonUnmarshallers.*;
import com.amazonaws.transform.*;

import com.fasterxml.jackson.core.JsonToken;
import static com.fasterxml.jackson.core.JsonToken.*;

/**
 * SearchItemsPutRequest JSON Unmarshaller
 */
public class SearchItemsPutRequestJsonUnmarshaller implements Unmarshaller<SearchItemsPutRequest, JsonUnmarshallerContext> {

    public SearchItemsPutRequest unmarshall(JsonUnmarshallerContext context) throws Exception {
        SearchItemsPutRequest searchItemsPutRequest = new SearchItemsPutRequest();

        int originalDepth = context.getCurrentDepth();
        String currentParentElement = context.getCurrentParentElement();
        int targetDepth = originalDepth + 1;

        JsonToken token = context.getCurrentToken();
        if (token == null)
            token = context.nextToken();
        if (token == VALUE_NULL)
            return null;

        while (true) {
            if (token == null)
                break;

            if (token == FIELD_NAME || token == START_OBJECT) {
                if (context.testExpression("after", targetDepth)) {
                    context.nextToken();
                    searchItemsPutRequest.setAfter(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("before", targetDepth)) {
                    context.nextToken();
                    searchItemsPutRequest.setBefore(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("query", targetDepth)) {
                    context.nextToken();
                    searchItemsPutRequest.setQuery(QueryJsonUnmarshaller.getInstance().unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return searchItemsPutRequest;
    }

    private static SearchItemsPutRequestJsonUnmarshaller instance;

    public static SearchItemsPutRequestJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new SearchItemsPutRequestJsonUnmarshaller();
        return instance;
    }
}
