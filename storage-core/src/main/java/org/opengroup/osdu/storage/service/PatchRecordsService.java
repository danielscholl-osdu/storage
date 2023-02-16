package org.opengroup.osdu.storage.service;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.storage.model.PatchRecordsRequestModel;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;

import java.util.List;
import java.util.Optional;

public interface PatchRecordsService {
    PatchRecordsResponse patchRecords(PatchRecordsRequestModel patchRecordsRequest, String user, Optional<CollaborationContext> collaborationContext);
}
