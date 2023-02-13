package org.opengroup.osdu.storage.util.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.github.fge.jsonpatch.AddOperation;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchOperation;
import com.github.fge.jsonpatch.RemoveOperation;
import com.github.fge.jsonpatch.ReplaceOperation;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.model.RecordPatchOperation;

import java.util.List;
import java.util.stream.Collectors;

public class PatchUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static JsonPatch convertPatchOpsToJsonPatch(List<RecordPatchOperation> patchOps) {

        List<JsonPatchOperation> operations = patchOps.stream()
                .map(PatchUtil::mapper)
                .collect(Collectors.toList());
        return new JsonPatch(operations);
    }

    private static JsonPatchOperation mapper(RecordPatchOperation operation) {
        JsonPointer path;
        JsonPatchOperation jsonPatchOperation = null;
        JsonNode jsonNode = objectMapper.convertValue(operation.getValue(), JsonNode.class);

        try {
            path = new JsonPointer(operation.getPath());
        } catch (JsonPointerException e) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid path", e.getMessage());
        }

        switch (PatchOperations.forOperation(operation.getOp())) {
            case ADD:
                jsonPatchOperation = new AddOperation(path, jsonNode);
                break;
            case REPLACE:
                jsonPatchOperation = new ReplaceOperation(path, jsonNode);
                break;
            case REMOVE:
                jsonPatchOperation = new RemoveOperation(path);
        }

        return jsonPatchOperation;
    }
}
