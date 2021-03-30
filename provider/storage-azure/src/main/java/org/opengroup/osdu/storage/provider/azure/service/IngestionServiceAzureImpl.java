package org.opengroup.osdu.storage.provider.azure.service;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.storage.service.IngestionServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@Primary
public class IngestionServiceAzureImpl extends IngestionServiceImpl {
    @Override
    public TransferInfo createUpdateRecords(boolean skipDupes, List<Record> inputRecords, String user) {
        this.validateIds(inputRecords);
        return super.createUpdateRecords(skipDupes, inputRecords, user);
    }

    private void validateIds(List<Record> inputRecords) {
        if (inputRecords.stream()
                .map(Record::getId)
                .filter(Objects::nonNull)
                .anyMatch(id -> id.length() > 100)) {
            String msg = "RecordId values which are exceeded 100 symbols temporarily not allowed";
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid id", msg);
        }
    }
}