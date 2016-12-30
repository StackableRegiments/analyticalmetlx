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
 * InputItemsPutRequest JSON Unmarshaller
 */
public class InputItemsPutRequestJsonUnmarshaller implements Unmarshaller<InputItemsPutRequest, JsonUnmarshallerContext> {

    public InputItemsPutRequest unmarshall(JsonUnmarshallerContext context) throws Exception {
        InputItemsPutRequest inputItemsPutRequest = new InputItemsPutRequest();

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
                if (context.testExpression("items", targetDepth)) {
                    context.nextToken();
                    inputItemsPutRequest.setItems(new ListUnmarshaller<InputItem>(InputItemJsonUnmarshaller.getInstance()).unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return inputItemsPutRequest;
    }

    private static InputItemsPutRequestJsonUnmarshaller instance;

    public static InputItemsPutRequestJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new InputItemsPutRequestJsonUnmarshaller();
        return instance;
    }
}
