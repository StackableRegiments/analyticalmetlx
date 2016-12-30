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
 * GroupSet JSON Unmarshaller
 */
public class GroupSetJsonUnmarshaller implements Unmarshaller<GroupSet, JsonUnmarshallerContext> {

    public GroupSet unmarshall(JsonUnmarshallerContext context) throws Exception {
        GroupSet groupSet = new GroupSet();

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
                if (context.testExpression("groups", targetDepth)) {
                    context.nextToken();
                    groupSet.setGroups(new ListUnmarshaller<Group>(GroupJsonUnmarshaller.getInstance()).unmarshall(context));
                }
                if (context.testExpression("name", targetDepth)) {
                    context.nextToken();
                    groupSet.setName(context.getUnmarshaller(String.class).unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return groupSet;
    }

    private static GroupSetJsonUnmarshaller instance;

    public static GroupSetJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new GroupSetJsonUnmarshaller();
        return instance;
    }
}
