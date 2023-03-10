package org.opengroup.osdu.storage.validation.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.storage.util.api.PatchOperations;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatchPath;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static org.opengroup.osdu.storage.util.api.PatchOperations.ADD;
import static org.opengroup.osdu.storage.util.api.PatchOperations.REMOVE;

public class JsonPatchPathValidator implements ConstraintValidator<ValidJsonPatchPath, JsonPatch> {
    private static final Set<String> VALID_PATH_BEGINNINGS = new HashSet<>(Arrays.asList("/tags", "/acl/viewers", "/acl/owners", "/legal/legaltags", "/ancestry/parents", "/kind", "/data", "/meta"));
    private static final Set<String> INVALID_PATHS_FOR_REMOVE_OR_ADD_OPERATION = new HashSet<>(Arrays.asList("/acl/viewers", "/acl/owners", "/legal/legaltags"));
    private static final String PATH = "path";
    private static final String OP = "op";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isValid(JsonPatch jsonPatch, ConstraintValidatorContext context) {
        boolean isPathStartValid;
        boolean isPathEndValid;
        boolean isValidPathForRemoveOperations;

        isPathStartValid = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .map(operation -> removeExtraQuotes(operation.get(PATH)))
                .allMatch(getAllowedPaths());
        if (!isPathStartValid) {
            throw RequestValidationException.builder()
                    .message(ValidationDoc.INVALID_PATCH_PATH)
                    .build();
        }

        isPathEndValid = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .map(operation -> removeExtraQuotes(operation.get(PATH)))
                .noneMatch(getInvalidPathEnd());
        if (!isPathEndValid) {
            throw RequestValidationException.builder()
                    .message(ValidationDoc.INVALID_PATCH_PATH_END)
                    .build();
        }

        isValidPathForRemoveOperations = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(addOrRemoveOperation())
                .map(operation -> removeExtraQuotes(operation.get(PATH)))
                .noneMatch(getInvalidPathsForRemoveOperation());
        if (!isValidPathForRemoveOperations) {
            throw RequestValidationException.builder()
                    .message(ValidationDoc.INVALID_PATCH_PATH_FOR_ADD_OR_REMOVE_OPERATION)
                    .build();
        }

        return true;
    }

    private Predicate<String> getAllowedPaths() {
        return path -> VALID_PATH_BEGINNINGS.stream().anyMatch(path::startsWith);
    }

    private Predicate<? super String> getInvalidPathEnd() {
        return path -> path.endsWith("/");
    }

    private Predicate<JsonNode> addOrRemoveOperation() {
        return operation -> {
            PatchOperations patchOperation = PatchOperations.forOperation(removeExtraQuotes(operation.get(OP)));
            return ADD.equals(patchOperation) || REMOVE.equals(patchOperation);
        };
    }

    private Predicate<String> getInvalidPathsForRemoveOperation() {
        return path -> INVALID_PATHS_FOR_REMOVE_OR_ADD_OPERATION.stream().anyMatch(path::equals);
    }

    private String removeExtraQuotes(JsonNode jsonNode) {
        return jsonNode.toString().replace("\"", "");
    }

}
