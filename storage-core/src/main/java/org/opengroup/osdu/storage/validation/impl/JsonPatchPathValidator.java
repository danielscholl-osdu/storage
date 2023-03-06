package org.opengroup.osdu.storage.validation.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.storage.util.api.PatchOperations;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatchPath;
import org.springframework.http.HttpStatus;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static org.opengroup.osdu.storage.util.api.PatchOperations.REMOVE;

public class JsonPatchPathValidator implements ConstraintValidator<ValidJsonPatchPath, JsonPatch> {
    private static final Set<String> VALID_PATH_BEGINNINGS = new HashSet<>(Arrays.asList("/tags", "/acl/viewers", "/acl/owners", "/legal/legaltags", "/ancestry/parents", "/kind", "/data", "/meta"));
    private static final Set<String> INVALID_PATHS_FOR_REMOVE_OPERATION = new HashSet<>(Arrays.asList("/acl/viewers", "/acl/owners", "/legal/legaltags"));
    private static final String PATH = "path";
    private static final String OP = "op";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isValid(JsonPatch jsonPatch, ConstraintValidatorContext context) {
        boolean isPathStartValid;
        boolean isValidPathForRemoveOperations;

        isPathStartValid = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .map(operation -> removeExtraQuotes(operation.get(PATH)))
                .allMatch(getAllowedPaths());
        if (!isPathStartValid) {
            throw RequestValidationException.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message(ValidationDoc.INVALID_PATCH_PATH)
                    .build();
        }

        isValidPathForRemoveOperations = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(removeOperation())
                .map(operation -> removeExtraQuotes(operation.get(PATH)))
                .noneMatch(getInvalidPathsForRemoveOperation());

        if (!isValidPathForRemoveOperations) {
            throw RequestValidationException.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message(ValidationDoc.INVALID_PATCH_OPERATION_PATH_FOR_REMOVE_OPERATION)
                    .build();
        }

        return true;
    }

    private Predicate<String> getAllowedPaths() {
        return path -> VALID_PATH_BEGINNINGS.stream().anyMatch(path::startsWith);
    }

    private Predicate<JsonNode> removeOperation() {
        return operation -> REMOVE.equals(PatchOperations.forOperation(removeExtraQuotes(operation.get(OP))));
    }

    private Predicate<String> getInvalidPathsForRemoveOperation() {
        return path -> INVALID_PATHS_FOR_REMOVE_OPERATION.stream().anyMatch(path::equals);
    }

    private String removeExtraQuotes(JsonNode jsonNode) {
        return jsonNode.toString().replace("\"", "");
    }

}
