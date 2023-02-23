package org.opengroup.osdu.storage.validation.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.storage.util.api.PatchOperations;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatchOperation;
import org.springframework.http.HttpStatus;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static org.opengroup.osdu.storage.util.api.PatchOperations.ADD;
import static org.opengroup.osdu.storage.util.api.PatchOperations.REMOVE;
import static org.opengroup.osdu.storage.util.api.PatchOperations.REPLACE;

public class JsonPatchOperationValidator implements ConstraintValidator<ValidJsonPatchOperation, JsonPatch> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isValid(JsonPatch jsonPatch, ConstraintValidatorContext context) {
        boolean isValid;
        isValid = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .map(operation -> operation.get("op").toString().replace("\"", ""))
                .map(PatchOperations::forOperation)
                .allMatch(getAllowedOperations());
        if(!isValid) {
            throw RequestValidationException.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message(ValidationDoc.INVALID_PATCH_OPERATION)
                    .build();
        }
        return isValid;
    }

    private Predicate<PatchOperations> getAllowedOperations() {
        return operation -> ADD.equals(operation) || REMOVE.equals(operation) || REPLACE.equals(operation);
    }
}
