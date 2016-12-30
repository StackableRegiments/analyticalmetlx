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
 * PutSearchResult JSON Unmarshaller
 */
public class PutSearchResultJsonUnmarshaller implements Unmarshaller<PutSearchResult, JsonUnmarshallerContext> {

    public PutSearchResult unmarshall(JsonUnmarshallerContext context) throws Exception {
        PutSearchResult putSearchResult = new PutSearchResult();

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

            putSearchResult.setSearchItemsResponse(new ListUnmarshaller<SearchResponse>(SearchResponseJsonUnmarshaller.getInstance()).unmarshall(context));
            token = context.nextToken();
        }

        return putSearchResult;
    }

    private static PutSearchResultJsonUnmarshaller instance;

    public static PutSearchResultJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new PutSearchResultJsonUnmarshaller();
        return instance;
    }
}
