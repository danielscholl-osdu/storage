package org.opengroup.osdu.storage.provider.azure.service;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.storage.model.PatchRecordsRequestModel;
import org.opengroup.osdu.storage.provider.azure.util.RecordUtil;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.service.PatchRecordsServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Primary
public class PatchRecordsServiceAzureImpl extends PatchRecordsServiceImpl {
    private final RecordUtil recordUtil;

    public PatchRecordsServiceAzureImpl(RecordUtil recordUtil) {
        this.recordUtil = recordUtil;
    }

    public PatchRecordsResponse patchRecords(PatchRecordsRequestModel patchRecordsRequest, String user, Optional<CollaborationContext> collaborationContext) {
        recordUtil.validateIds(patchRecordsRequest.getQuery().getIds());
        return super.patchRecords(patchRecordsRequest, user, collaborationContext);
    }
}
