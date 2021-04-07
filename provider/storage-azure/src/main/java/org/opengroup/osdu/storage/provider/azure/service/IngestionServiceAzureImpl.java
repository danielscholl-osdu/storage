package org.opengroup.osdu.storage.provider.azure.service;

import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.storage.provider.azure.util.RecordIdValidator;
import org.opengroup.osdu.storage.service.IngestionServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@Primary
public class IngestionServiceAzureImpl extends IngestionServiceImpl {

    private final RecordIdValidator recordIdValidator;

    public IngestionServiceAzureImpl(RecordIdValidator recordIdValidator) {
        this.recordIdValidator = recordIdValidator;
    }

    @Override
    public TransferInfo createUpdateRecords(boolean skipDupes, List<Record> inputRecords, String user) {
        recordIdValidator.validateIds(inputRecords.stream().map(Record::getId).collect(toList()));
        return super.createUpdateRecords(skipDupes, inputRecords, user);
    }
}