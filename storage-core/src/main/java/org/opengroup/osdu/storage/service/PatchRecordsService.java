package org.opengroup.osdu.storage.service;

import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PatchRecordsService {
    PatchRecordsResponse patchRecords(List<String> ids, Map<String, JsonPatch> jsonPatch, String user, Optional<CollaborationContext> collaborationContext);
}
