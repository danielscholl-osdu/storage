package org.opengroup.osdu.storage.validation.impl;

import com.google.common.base.Strings;
import org.opengroup.osdu.storage.model.RecordPatchOperation;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidPatchOperation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class PatchValidator implements ConstraintValidator<ValidPatchOperation, RecordPatchOperation> {
    private static final String OPERATION_ADD = "add";
    private static final String OPERATION_REMOVE = "remove";
    private static final String OPERATION_REPLACE = "replace";
    private static final Set<String> VALID_PATHS_FOR_PATCH = new HashSet<>(Arrays.asList("tags", "acl", "legal", "ancestry", "kind", "data", "meta"));

    @Override
    public void initialize(ValidPatchOperation constraintAnnotation) {
        //do nothing
    }

    @Override
    public boolean isValid(RecordPatchOperation operation, ConstraintValidatorContext context) {
        Predicate<String> allowedOperations;

        if (operation == null || Strings.isNullOrEmpty(operation.getPath())) {
            context.buildConstraintViolationWithTemplate(ValidationDoc.INVALID_PATCH_PATH).addConstraintViolation();
            return false;
        }

        String[] pathComponent = operation.getPath().split("/");
        String firstPathComponent = pathComponent[1];

        if (VALID_PATHS_FOR_PATCH.contains(firstPathComponent)) {
            allowedOperations = getAllowedOperations();
            if (!allowedOperations.test(operation.getOp())) {
                context.buildConstraintViolationWithTemplate(ValidationDoc.INVALID_PATCH_OPERATION).addConstraintViolation();
                return false;
            }
        } else {
            context.buildConstraintViolationWithTemplate(ValidationDoc.INVALID_PATCH_PATH).addConstraintViolation();
            return false;
        }
        return true;
    }


    private Predicate<String> getAllowedOperations() {
        return operation -> OPERATION_ADD.equals(operation) ||
                OPERATION_REMOVE.equals(operation) ||
                OPERATION_REPLACE.equals(operation);
    }
}
