package org.opengroup.osdu.storage.validation.impl;

import org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.model.RecordQueryPatch;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.api.ValidBulkQueryPatch;
import org.springframework.util.CollectionUtils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.opengroup.osdu.storage.util.RecordConstants.MAX_RECORD_ID_NUMBER;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_RECORD_ID_PATCH;
import static org.opengroup.osdu.storage.validation.ValidationDoc.PATCH_RECORDS_MAX;
import static org.opengroup.osdu.storage.validation.ValidationDoc.RECORD_ID_LIST_NOT_EMPTY;

public class BulkQueryPatchValidator implements ConstraintValidator<ValidBulkQueryPatch, RecordQueryPatch> {


    @Override
    public void initialize(ValidBulkQueryPatch constraintAnnotation) {
        //do nothing
    }

    @Override
    public boolean isValid(RecordQueryPatch value, ConstraintValidatorContext context) {
        if (value == null) {
            throw RequestValidationException.builder()
                    .message(ValidationDoc.INVALID_PAYLOAD)
                    .build();
        }

        List<String> recordIds = value.getIds();

        if (CollectionUtils.isEmpty(recordIds)) {
            throw RequestValidationException.builder()
                    .message(RECORD_ID_LIST_NOT_EMPTY)
                    .build();
        }

        if (recordIds.size() > MAX_RECORD_ID_NUMBER) {
            throw RequestValidationException.builder()
                    .message(PATCH_RECORDS_MAX)
                    .build();
        }

        Set<String> ids = new HashSet<>();
        for (String recordId : recordIds) {
            if (ids.contains(recordId)) {
                throw RequestValidationException.builder()
                        .message(ValidationDoc.DUPLICATE_RECORD_ID)
                        .build();
            }
            if (!recordId.matches(ValidationDoc.RECORD_ID_REGEX)) {
                throw RequestValidationException.builder()
                        .message(INVALID_RECORD_ID_PATCH)
                        .build();
            }
            ids.add(recordId);
        }
        return true;
    }
}
