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
 * SmartGroupsRequest JSON Unmarshaller
 */
public class SmartGroupsRequestJsonUnmarshaller implements Unmarshaller<SmartGroupsRequest, JsonUnmarshallerContext> {

    public SmartGroupsRequest unmarshall(JsonUnmarshallerContext context) throws Exception {
        SmartGroupsRequest smartGroupsRequest = new SmartGroupsRequest();

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
                if (context.testExpression("groupCount", targetDepth)) {
                    context.nextToken();
                    smartGroupsRequest.setGroupCount(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("members", targetDepth)) {
                    context.nextToken();
                    smartGroupsRequest.setMembers(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return smartGroupsRequest;
    }

    private static SmartGroupsRequestJsonUnmarshaller instance;

    public static SmartGroupsRequestJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new SmartGroupsRequestJsonUnmarshaller();
        return instance;
    }
}
