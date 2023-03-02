package org.opengroup.osdu.storage.validation.impl;

import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.core.common.model.storage.validation.ValidBulkQuery;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidInputSize;
import org.springframework.http.HttpStatus;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

public class InputSizeValidator implements ConstraintValidator<ValidInputSize, RecordQuery> {
    @Override
    public void initialize(ValidInputSize constraintAnnotation) {
        // do nothing
    }

    @Override
    public boolean isValid(RecordQuery recordQuery, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        boolean isValid;
        int min_input_size = 1;
        int max_input_size = 100;
        List<String> recordIds = recordQuery.getIds();

        isValid = recordIds.size() >= min_input_size && recordIds.size() <= max_input_size;

        if (!isValid) {
            throw RequestValidationException.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message(ValidationDoc.INVALID_INPUT_SIZE)
                    .build();
        }
        return isValid;
    }

}
