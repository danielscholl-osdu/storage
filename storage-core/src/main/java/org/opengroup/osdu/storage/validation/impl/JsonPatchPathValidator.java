package org.opengroup.osdu.storage.validation.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatchPath;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class JsonPatchPathValidator implements ConstraintValidator<ValidJsonPatchPath, JsonPatch> {
    private static final Set<String> VALID_PATHS = new HashSet<>(Arrays.asList("/tags", "/acl", "/legal/legaltags", "/ancestry/parents", "/kind", "/data", "/meta"));
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void initialize(ValidJsonPatchPath constraintAnnotation) {
        //do nothing
    }

    @Override
    public boolean isValid(JsonPatch jsonPatch, ConstraintValidatorContext context) {
        return StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), true)
                .map(operation -> operation.get("path").toString().replace("\"", ""))
                .allMatch(getAllowedPaths());
    }

    private Predicate<String> getAllowedPaths() {
        return path -> VALID_PATHS.stream().anyMatch(path::startsWith);
    }

}
