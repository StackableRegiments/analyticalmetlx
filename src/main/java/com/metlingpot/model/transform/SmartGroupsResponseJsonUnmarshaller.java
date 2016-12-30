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
 * SmartGroupsResponse JSON Unmarshaller
 */
public class SmartGroupsResponseJsonUnmarshaller implements Unmarshaller<SmartGroupsResponse, JsonUnmarshallerContext> {

    public SmartGroupsResponse unmarshall(JsonUnmarshallerContext context) throws Exception {
        SmartGroupsResponse smartGroupsResponse = new SmartGroupsResponse();

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
                if (context.testExpression("groupSets", targetDepth)) {
                    context.nextToken();
                    smartGroupsResponse.setGroupSets(new ListUnmarshaller<GroupSet>(GroupSetJsonUnmarshaller.getInstance()).unmarshall(context));
                }
                if (context.testExpression("members", targetDepth)) {
                    context.nextToken();
                    smartGroupsResponse.setMembers(new ListUnmarshaller<String>(context.getUnmarshaller(String.class)).unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return smartGroupsResponse;
    }

    private static SmartGroupsResponseJsonUnmarshaller instance;

    public static SmartGroupsResponseJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new SmartGroupsResponseJsonUnmarshaller();
        return instance;
    }
}
