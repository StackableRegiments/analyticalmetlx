/**
 * apache2
 */
package com.metlingpot.model.transform;

import java.util.Map;
import java.util.List;

import com.amazonaws.SdkClientException;
import com.metlingpot.model.*;
import com.amazonaws.transform.Marshaller;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.StringUtils;
import com.amazonaws.util.IdempotentUtils;
import com.amazonaws.util.StringInputStream;
import com.amazonaws.protocol.json.*;

/**
 * InsertDBResponseMarshaller
 */
public class InsertDBResponseJsonMarshaller {

    /**
     * Marshall the given parameter object, and output to a SdkJsonGenerator
     */
    public void marshall(InsertDBResponse insertDBResponse, StructuredJsonGenerator jsonGenerator) {

        if (insertDBResponse == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            jsonGenerator.writeStartObject();

            if (insertDBResponse.getAffectedRows() != null) {
                jsonGenerator.writeFieldName("affectedRows").writeValue(insertDBResponse.getAffectedRows());
            }
            if (insertDBResponse.getChangedRows() != null) {
                jsonGenerator.writeFieldName("changedRows").writeValue(insertDBResponse.getChangedRows());
            }
            if (insertDBResponse.getFieldCount() != null) {
                jsonGenerator.writeFieldName("fieldCount").writeValue(insertDBResponse.getFieldCount());
            }
            if (insertDBResponse.getInsertId() != null) {
                jsonGenerator.writeFieldName("insertId").writeValue(insertDBResponse.getInsertId());
            }
            if (insertDBResponse.getMessage() != null) {
                jsonGenerator.writeFieldName("message").writeValue(insertDBResponse.getMessage());
            }
            if (insertDBResponse.getProtocol41() != null) {
                jsonGenerator.writeFieldName("protocol41").writeValue(insertDBResponse.getProtocol41());
            }
            if (insertDBResponse.getServerStatus() != null) {
                jsonGenerator.writeFieldName("serverStatus").writeValue(insertDBResponse.getServerStatus());
            }
            if (insertDBResponse.getWarningCount() != null) {
                jsonGenerator.writeFieldName("warningCount").writeValue(insertDBResponse.getWarningCount());
            }

            jsonGenerator.writeEndObject();
        } catch (Throwable t) {
            throw new SdkClientException("Unable to marshall request to JSON: " + t.getMessage(), t);
        }
    }

    private static InsertDBResponseJsonMarshaller instance;

    public static InsertDBResponseJsonMarshaller getInstance() {
        if (instance == null)
            instance = new InsertDBResponseJsonMarshaller();
        return instance;
    }

}
