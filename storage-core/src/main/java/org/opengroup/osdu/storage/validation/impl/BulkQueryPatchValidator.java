package org.opengroup.osdu.storage.validation.impl;

import org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.model.RecordQueryPatch;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.api.ValidBulkQueryPatch;
import org.springframework.http.HttpStatus;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_RECORD_ID_PATCH;

public class BulkQueryPatchValidator implements ConstraintValidator<ValidBulkQueryPatch, RecordQueryPatch> {


    @Override
    public void initialize(ValidBulkQueryPatch constraintAnnotation) {
        //do nothing
    }

    @Override
    public boolean isValid(RecordQueryPatch value, ConstraintValidatorContext context) {
        if(value == null){
            throw RequestValidationException.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message(ValidationDoc.INVALID_PAYLOAD)
                    .build();
        }

        List<String> recordIds = value.getIds();
        Set<String> ids = new HashSet<>();
        for (String recordId : recordIds) {
            if (ids.contains(recordId)) {
                throw RequestValidationException.builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .message(ValidationDoc.DUPLICATE_RECORD_ID)
                        .build();
            }
            if (!recordId.matches(ValidationDoc.RECORD_ID_REGEX)) {
                throw RequestValidationException.builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .message(INVALID_RECORD_ID_PATCH)
                        .build();
            }
            ids.add(recordId);
        }
        return true;
    }
}
