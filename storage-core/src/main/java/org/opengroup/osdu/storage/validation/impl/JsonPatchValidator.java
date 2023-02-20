package org.opengroup.osdu.storage.validation.impl;

import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatch;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class JsonPatchValidator implements ConstraintValidator<ValidJsonPatch, JsonPatch> {
    private static final String OPERATION_ADD = "add";
    private static final String OPERATION_REMOVE = "remove";
    private static final String OPERATION_REPLACE = "replace";
    private static final Set<String> VALID_PATHS_FOR_PATCH = new HashSet<>(Arrays.asList("tags", "acl", "legal", "ancestry", "kind", "data", "meta"));

    @Override
    public void initialize(ValidJsonPatch constraintAnnotation) {
        //do nothing
    }

    @Override
    public boolean isValid(JsonPatch jsonPatch, ConstraintValidatorContext context) {
        //TODO: implement validation logic
        return true;
    }


    private Predicate<String> getAllowedOperations() {
        return operation -> OPERATION_ADD.equals(operation) ||
                OPERATION_REMOVE.equals(operation) ||
                OPERATION_REPLACE.equals(operation);
    }

}
