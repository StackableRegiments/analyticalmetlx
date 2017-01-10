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
 * InsertDBResponse JSON Unmarshaller
 */
public class InsertDBResponseJsonUnmarshaller implements Unmarshaller<InsertDBResponse, JsonUnmarshallerContext> {

    public InsertDBResponse unmarshall(JsonUnmarshallerContext context) throws Exception {
        InsertDBResponse insertDBResponse = new InsertDBResponse();

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
                if (context.testExpression("affectedRows", targetDepth)) {
                    context.nextToken();
                    insertDBResponse.setAffectedRows(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("changedRows", targetDepth)) {
                    context.nextToken();
                    insertDBResponse.setChangedRows(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("fieldCount", targetDepth)) {
                    context.nextToken();
                    insertDBResponse.setFieldCount(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("insertId", targetDepth)) {
                    context.nextToken();
                    insertDBResponse.setInsertId(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("message", targetDepth)) {
                    context.nextToken();
                    insertDBResponse.setMessage(context.getUnmarshaller(String.class).unmarshall(context));
                }
                if (context.testExpression("protocol41", targetDepth)) {
                    context.nextToken();
                    insertDBResponse.setProtocol41(context.getUnmarshaller(Boolean.class).unmarshall(context));
                }
                if (context.testExpression("serverStatus", targetDepth)) {
                    context.nextToken();
                    insertDBResponse.setServerStatus(context.getUnmarshaller(Double.class).unmarshall(context));
                }
                if (context.testExpression("warningCount", targetDepth)) {
                    context.nextToken();
                    insertDBResponse.setWarningCount(context.getUnmarshaller(Double.class).unmarshall(context));
                }
            } else if (token == END_ARRAY || token == END_OBJECT) {
                if (context.getLastParsedParentElement() == null || context.getLastParsedParentElement().equals(currentParentElement)) {
                    if (context.getCurrentDepth() <= originalDepth)
                        break;
                }
            }
            token = context.nextToken();
        }

        return insertDBResponse;
    }

    private static InsertDBResponseJsonUnmarshaller instance;

    public static InsertDBResponseJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new InsertDBResponseJsonUnmarshaller();
        return instance;
    }
}
