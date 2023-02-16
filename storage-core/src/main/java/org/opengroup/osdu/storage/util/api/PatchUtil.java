package org.opengroup.osdu.storage.util.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.github.fge.jsonpatch.AddOperation;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchOperation;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.model.RecordPatchOperation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PatchUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String REFERENCE_TO_THE_END_OF_THE_ARRAY = "/-";

    public static JsonPatch convertPatchOpsToJsonPatch(List<RecordPatchOperation> patchOps) {

        List<JsonPatchOperation> operations = patchOps.stream()
                .map(PatchUtil::mapper)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        return new JsonPatch(operations);
    }

    private static List<JsonPatchOperation> mapper(RecordPatchOperation operation) {
        List<JsonPatchOperation> jsonPatchOperations = new ArrayList<>();

        switch (PatchOperations.forOperation(operation.getOp())) {
            case ADD:
                jsonPatchOperations.addAll(getAddOperations(operation));
                break;
            case REPLACE:
                jsonPatchOperations.addAll(getReplaceOperations(operation));
                break;
            case REMOVE:
                //We can only delete values by a specific index, not the value itself (existing contract)
                jsonPatchOperations.addAll(getRemoveOperations(operation));
                break;
            case UNDEFINED:
                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid operation", "Add, replace and remove operations are supported");
        }

        return jsonPatchOperations;
    }

    private static List<JsonPatchOperation> getAddOperations(RecordPatchOperation operation) {

        return Arrays.stream(operation.getValue())
                .map(value -> new AddOperation(getPath(operation), objectMapper.convertValue(value, JsonNode.class)))
                .collect(Collectors.toList());
    }

    private static List<JsonPatchOperation> getReplaceOperations(RecordPatchOperation operation) {
        //For replace operation we use adding array of all values in place of the existing array
        JsonNode targetValue;
        if (isOperationOnArrayValue(operation.getPath())) {
            targetValue = objectMapper.convertValue(operation.getValue(), JsonNode.class);
        } else {
            targetValue = objectMapper.convertValue(operation.getValue()[0], JsonNode.class);
        }

        return Collections.singletonList(new AddOperation(getPath(operation), targetValue));
    }

    private static List<JsonPatchOperation> getRemoveOperations(RecordPatchOperation operation) {

        return null;
    }

    private static JsonPointer getPath(RecordPatchOperation operation) {
        String path = operation.getPath();
        String operationPath;

        if (isOperationOnArrayValue(path) && PatchOperations.forOperation(operation.getOp()).equals(PatchOperations.ADD)) {
            operationPath = path.concat(REFERENCE_TO_THE_END_OF_THE_ARRAY);
        } else {
            operationPath = path;
        }

        JsonPointer jsonPointer;
        try {
            jsonPointer = new JsonPointer(operationPath);
        } catch (JsonPointerException e) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid path", e.getMessage());
        }
        return jsonPointer;
    }

    private static boolean isOperationOnArrayValue(String path) {
        return path.equals("/acl/viewers")
                || path.equals("/acl/owners")
                || path.equals("/legal/legaltags")
                || path.equals("/tags");
    }

}
