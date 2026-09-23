package org.opengroup.osdu.storage.provider.azure.service;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.storage.provider.azure.util.RecordUtil;
import org.opengroup.osdu.storage.response.BulkUpdateRecordsResponse;
import org.opengroup.osdu.storage.service.BulkUpdateRecordServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Primary
public class BulkUpdateRecordServiceAzureImpl extends BulkUpdateRecordServiceImpl {

    private final RecordUtil recordUtil;

    public BulkUpdateRecordServiceAzureImpl(RecordUtil recordUtil) {
        this.recordUtil = recordUtil;
    }

    public BulkUpdateRecordsResponse bulkUpdateRecords(RecordBulkUpdateParam recordBulkUpdateParam, String user, Optional<CollaborationContext> collaborationContext) {
        recordUtil.validateIds(recordBulkUpdateParam.getQuery().getIds());
        return super.bulkUpdateRecords(recordBulkUpdateParam, user, collaborationContext);
    }

}
