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
 * Query JSON Unmarshaller
 */
public class QueryJsonUnmarshaller implements Unmarshaller<Query, JsonUnmarshallerContext> {

    public Query unmarshall(JsonUnmarshallerContext context) throws Exception {
        Query query = new Query();

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
                    query.setActionname(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
                if (context.testExpression("actiontype", targetDepth)) {
                    context.nextToken();
                    query.setActiontype(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
                if (context.testExpression("actorname", targetDepth)) {
                    context.nextToken();
                    query.setActorname(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
                if (context.testExpression("actortype", targetDepth)) {
                    context.nextToken();
                    query.setActortype(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
                if (context.testExpression("contextname", targetDepth)) {
                    context.nextToken();
                    query.setContextname(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
                if (context.testExpression("contexttype", targetDepth)) {
                    context.nextToken();
                    query.setContexttype(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
                if (context.testExpression("source", targetDepth)) {
                    context.nextToken();
                    query.setSource(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
                if (context.testExpression("targetname", targetDepth)) {
                    context.nextToken();
                    query.setTargetname(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
                if (context.testExpression("targettype", targetDepth)) {
                    context.nextToken();
                    query.setTargettype(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return query;
    }

    private static QueryJsonUnmarshaller instance;

    public static QueryJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new QueryJsonUnmarshaller();
        return instance;
    }
}
