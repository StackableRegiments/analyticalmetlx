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
 * SearchResponse JSON Unmarshaller
 */
public class SearchResponseJsonUnmarshaller implements Unmarshaller<SearchResponse, JsonUnmarshallerContext> {

    public SearchResponse unmarshall(JsonUnmarshallerContext context) throws Exception {
        SearchResponse searchResponse = new SearchResponse();

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
                if (context.testExpression("count", targetDepth)) {
                    context.nextToken();
                    searchResponse.setCount(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("items", targetDepth)) {
                    context.nextToken();
                    searchResponse.setItems(new ListUnmarshaller<GetDBResponse>(GetDBResponseJsonUnmarshaller.getInstance()).unmarshall(context));
                }
                if (context.testExpression("query", targetDepth)) {
                    context.nextToken();
                    searchResponse.setQuery(context.getUnmarshaller(String.class).unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return searchResponse;
    }

    private static SearchResponseJsonUnmarshaller instance;

    public static SearchResponseJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new SearchResponseJsonUnmarshaller();
        return instance;
    }
}
