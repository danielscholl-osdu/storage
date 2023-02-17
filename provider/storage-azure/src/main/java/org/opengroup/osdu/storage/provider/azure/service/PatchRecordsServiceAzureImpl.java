package org.opengroup.osdu.storage.provider.azure.service;

import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.storage.provider.azure.util.RecordUtil;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.service.PatchRecordsServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Primary
public class PatchRecordsServiceAzureImpl extends PatchRecordsServiceImpl {
    private final RecordUtil recordUtil;

    public PatchRecordsServiceAzureImpl(RecordUtil recordUtil) {
        this.recordUtil = recordUtil;
    }

    public PatchRecordsResponse patchRecords(List<String> recordIds, Map<String, JsonPatch> patchPayload, String user, Optional<CollaborationContext> collaborationContext) {
        recordUtil.validateIds(recordIds);
        return super.patchRecords(recordIds, patchPayload, user, collaborationContext);
    }
}
