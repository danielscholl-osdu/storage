package org.opengroup.osdu.storage.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.github.fge.jsonpatch.AddOperation;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchOperation;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.storage.model.RecordPatchOperation;
import org.opengroup.osdu.storage.service.QueryService;
import org.opengroup.osdu.storage.util.api.PatchOperations;
import org.opengroup.osdu.storage.util.api.PatchUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PatchUtilImpl implements PatchUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String REFERENCE_TO_THE_END_OF_THE_ARRAY = "/-";

    @Autowired
    private QueryService queryService;

    @Override
    public Map<String, JsonPatch> convertPatchOpsToJsonPatch(List<String> ids, List<RecordPatchOperation> patchOps, Optional<CollaborationContext> collaborationContext) {
        Map<String, JsonPatch> idsToPatches = new HashMap<>();

        for (String id: ids) {
            List<JsonPatchOperation> operations = patchOps.stream()
                    .map(operation -> mapper(id, operation, collaborationContext))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            idsToPatches.put(id, new JsonPatch(operations));
        }

        return idsToPatches;
    }

    private List<JsonPatchOperation> mapper(String id, RecordPatchOperation operation, Optional<CollaborationContext> collaborationContext) {
        List<JsonPatchOperation> jsonPatchOperations = new ArrayList<>();

        switch (PatchOperations.forOperation(operation.getOp())) {
            case ADD:
                jsonPatchOperations.addAll(getAddOperations(operation));
                break;
            case REPLACE:
                jsonPatchOperations.addAll(getReplaceOperations(operation));
                break;
            case REMOVE:
                jsonPatchOperations.addAll(getRemoveOperations(id, operation, collaborationContext));
                break;
            case UNDEFINED:
                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid operation", "Add, replace and remove operations are supported");
        }

        return jsonPatchOperations;
    }

    private List<JsonPatchOperation> getAddOperations(RecordPatchOperation operation) {

        return Arrays.stream(operation.getValue())
                .map(value -> new AddOperation(getPath(operation), objectMapper.convertValue(value, JsonNode.class)))
                .collect(Collectors.toList());
    }

    private List<JsonPatchOperation> getReplaceOperations(RecordPatchOperation operation) {
        //For replace operation we use adding array of all values in place of the existing array
        JsonNode targetValue;
        if (isOperationOnArrayValue(operation.getPath())) {
            targetValue = objectMapper.convertValue(operation.getValue(), JsonNode.class);
        } else {
            targetValue = objectMapper.convertValue(operation.getValue()[0], JsonNode.class);
        }

        return Collections.singletonList(new AddOperation(getPath(operation), targetValue));
    }

    private List<JsonPatchOperation> getRemoveOperations(String recordId, RecordPatchOperation operation, Optional<CollaborationContext> collaborationContext) {
        String jsonRecord = queryService.getRecordInfo(recordId, null, collaborationContext);
        try {
            Record record = objectMapper.readValue(jsonRecord, Record.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        //Get the record content
        //TODO Delete necessary values
        //TODO Create add operation with remaining values in place of the existing values

        return Collections.singletonList(new AddOperation(getPath(operation), null));
    }

    private JsonPointer getPath(RecordPatchOperation operation) {
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

    private boolean isOperationOnArrayValue(String path) {
        return path.equals("/acl/viewers")
                || path.equals("/acl/owners")
                || path.equals("/legal/legaltags")
                || path.equals("/tags");
    }

}
