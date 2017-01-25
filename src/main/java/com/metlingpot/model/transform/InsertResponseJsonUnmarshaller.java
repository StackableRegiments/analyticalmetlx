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
 * InsertResponse JSON Unmarshaller
 */
public class InsertResponseJsonUnmarshaller implements Unmarshaller<InsertResponse, JsonUnmarshallerContext> {

    public InsertResponse unmarshall(JsonUnmarshallerContext context) throws Exception {
        InsertResponse insertResponse = new InsertResponse();

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
                if (context.testExpression("dbResponse", targetDepth)) {
                    context.nextToken();
                    insertResponse.setDbResponse(InsertDBResponseJsonUnmarshaller.getInstance().unmarshall(context));
                }
                if (context.testExpression("index", targetDepth)) {
                    context.nextToken();
                    insertResponse.setIndex(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("item", targetDepth)) {
                    context.nextToken();
                    insertResponse.setItem(ItemJsonUnmarshaller.getInstance().unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return insertResponse;
    }

    private static InsertResponseJsonUnmarshaller instance;

    public static InsertResponseJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new InsertResponseJsonUnmarshaller();
        return instance;
    }
}
