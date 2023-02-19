package org.opengroup.osdu.storage.validation.impl;


import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidPatchOperationPath;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class PatchOperationPathValidator implements ConstraintValidator<ValidPatchOperationPath, String> {
    @Override
    public void initialize(ValidPatchOperationPath constraintAnnotation) {
        // do nothing
    }

    @Override
    public boolean isValid(String path, ConstraintValidatorContext context) {
        if (!path.equals("/acl/viewers")
                && !path.equals("/acl/owners")
                && !path.equals("/legal/legaltags")
                && !path.equals("/tags")
                && !path.equals("/kind")
                && !path.equals("/ancestry/parents")
                && !path.contains("/data")
                && !path.contains("/meta")) {
            context.buildConstraintViolationWithTemplate(ValidationDoc.INVALID_PATCH_PATH).addConstraintViolation();
            return false;
        }
        return true;
    }
}
