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
 * PutSmartgroupsResult JSON Unmarshaller
 */
public class PutSmartgroupsResultJsonUnmarshaller implements Unmarshaller<PutSmartgroupsResult, JsonUnmarshallerContext> {

    public PutSmartgroupsResult unmarshall(JsonUnmarshallerContext context) throws Exception {
        PutSmartgroupsResult putSmartgroupsResult = new PutSmartgroupsResult();

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

            putSmartgroupsResult.setSmartGroupsResponse(SmartGroupsResponseJsonUnmarshaller.getInstance().unmarshall(context));
            token = context.nextToken();
        }

        return putSmartgroupsResult;
    }

    private static PutSmartgroupsResultJsonUnmarshaller instance;

    public static PutSmartgroupsResultJsonUnmarshaller getInstance() {
        if (instance == null)
            instance = new PutSmartgroupsResultJsonUnmarshaller();
        return instance;
    }
}
