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
 * PutInputItemResult JSON Unmarshaller
 */
public class PutInputItemResultJsonUnmarshaller implements Unmarshaller<PutInputItemResult, JsonUnmarshallerContext> {

    public PutInputItemResult unmarshall(JsonUnmarshallerContext context) throws Exception {
        PutInputItemResult putInputItemResult = new PutInputItemResult();

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

            putInputItemResult.setInputItemsResponse(new ListUnmarshaller<InsertResponse>(InsertResponseJsonUnmarshaller.getInstance()).unmarshall(context));
            token = context.nextToken();
        }

        return putInputItemResult;
    }

    private static PutInputItemResultJsonUnmarshaller instance;

    public static PutInputItemResultJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new PutInputItemResultJsonUnmarshaller();
        return instance;
    }
}
