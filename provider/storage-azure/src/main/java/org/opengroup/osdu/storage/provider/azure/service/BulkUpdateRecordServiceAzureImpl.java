package org.opengroup.osdu.storage.provider.azure.service;

import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.storage.provider.azure.util.RecordIdValidator;
import org.opengroup.osdu.storage.response.BulkUpdateRecordsResponse;
import org.opengroup.osdu.storage.service.BulkUpdateRecordServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class BulkUpdateRecordServiceAzureImpl extends BulkUpdateRecordServiceImpl {

    private final RecordIdValidator recordIdValidator;

    public BulkUpdateRecordServiceAzureImpl(RecordIdValidator recordIdValidator) {
        this.recordIdValidator = recordIdValidator;
    }

    public BulkUpdateRecordsResponse bulkUpdateRecords(RecordBulkUpdateParam recordBulkUpdateParam, String user) {
        recordIdValidator.validateIds(recordBulkUpdateParam.getQuery().getIds());
        return super.bulkUpdateRecords(recordBulkUpdateParam, user);
    }

}
