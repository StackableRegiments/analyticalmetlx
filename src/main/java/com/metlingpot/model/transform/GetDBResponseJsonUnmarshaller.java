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
 * GetDBResponse JSON Unmarshaller
 */
public class GetDBResponseJsonUnmarshaller implements Unmarshaller<GetDBResponse, JsonUnmarshallerContext> {

    public GetDBResponse unmarshall(JsonUnmarshallerContext context) throws Exception {
        GetDBResponse getDBResponse = new GetDBResponse();

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
                if (context.testExpression("actionname", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setActionname(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("actiontype", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setActiontype(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("actorname", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setActorname(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("actortype", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setActortype(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("contextname", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setContextname(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("contexttype", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setContexttype(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("id", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setId(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("source", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setSource(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("targetname", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setTargetname(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("targettype", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setTargettype(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("timestamp", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setTimestamp(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("value", targetDepth)) {
                    context.nextToken();
                    getDBResponse.setValue(context.getUnmarshaller(String.class).unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return getDBResponse;
    }

    private static GetDBResponseJsonUnmarshaller instance;

    public static GetDBResponseJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new GetDBResponseJsonUnmarshaller();
        return instance;
    }
}
