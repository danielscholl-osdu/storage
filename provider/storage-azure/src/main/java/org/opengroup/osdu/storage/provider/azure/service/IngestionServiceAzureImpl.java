package org.opengroup.osdu.storage.provider.azure.service;

import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.storage.provider.azure.util.RecordUtil;
import org.opengroup.osdu.storage.service.IngestionServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@Primary
public class IngestionServiceAzureImpl extends IngestionServiceImpl {

    private final RecordUtil recordUtil;

    public IngestionServiceAzureImpl(RecordUtil recordUtil) {
        this.recordUtil = recordUtil;
    }

    @Override
    public TransferInfo createUpdateRecords(boolean skipDupes, List<Record> inputRecords, String user) {
        recordUtil.validateIds(inputRecords.stream().map(Record::getId).collect(toList()));
        return super.createUpdateRecords(skipDupes, inputRecords, user);
    }
}