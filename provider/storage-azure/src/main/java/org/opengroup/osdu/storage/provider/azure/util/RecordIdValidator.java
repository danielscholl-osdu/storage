package org.opengroup.osdu.storage.provider.azure.util;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class RecordIdValidator {

    private final int recordIdMaxLength;

    public RecordIdValidator(@Value("${record-id.max.length}") int recordIdMaxLength) {
        this.recordIdMaxLength = recordIdMaxLength;
    }

    public void validateIds(List<String> inputRecords) {
        if (inputRecords.stream().filter(Objects::nonNull)
                .anyMatch(id -> id.length() > recordIdMaxLength)) {
            String msg = "RecordId values which are exceeded 100 symbols temporarily not allowed";
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid id", msg);
        }
    }
}