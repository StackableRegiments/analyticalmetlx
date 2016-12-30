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
 * InputItem JSON Unmarshaller
 */
public class InputItemJsonUnmarshaller implements Unmarshaller<InputItem, JsonUnmarshallerContext> {

    public InputItem unmarshall(JsonUnmarshallerContext context) throws Exception {
        InputItem inputItem = new InputItem();

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
                if (context.testExpression("action", targetDepth)) {
                    context.nextToken();
                    inputItem.setAction(TypedValueJsonUnmarshaller.getInstance().unmarshall(context));
                }
                if (context.testExpression("actor", targetDepth)) {
                    context.nextToken();
                    inputItem.setActor(TypedValueJsonUnmarshaller.getInstance().unmarshall(context));
                }
                if (context.testExpression("context", targetDepth)) {
                    context.nextToken();
                    inputItem.setContext(TypedValueJsonUnmarshaller.getInstance().unmarshall(context));
                }
                if (context.testExpression("source", targetDepth)) {
                    context.nextToken();
                    inputItem.setSource(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("target", targetDepth)) {
                    context.nextToken();
                    inputItem.setTarget(TypedValueJsonUnmarshaller.getInstance().unmarshall(context));
                }
                if (context.testExpression("timestamp", targetDepth)) {
                    context.nextToken();
                    inputItem.setTimestamp(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("value", targetDepth)) {
                    context.nextToken();
                    inputItem.setValue(context.getUnmarshaller(String.class).unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return inputItem;
    }

    private static InputItemJsonUnmarshaller instance;

    public static InputItemJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new InputItemJsonUnmarshaller();
        return instance;
    }
}
