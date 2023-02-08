package org.opengroup.osdu.storage.service;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;

import java.util.Optional;

public interface PatchRecordsService {
    PatchRecordsResponse patchRecords(RecordBulkUpdateParam recordBulkUpdateParam, String user, Optional<CollaborationContext> collaborationContext);
}
