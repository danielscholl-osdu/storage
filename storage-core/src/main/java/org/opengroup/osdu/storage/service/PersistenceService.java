package org.opengroup.osdu.storage.service;

import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.TransferBatch;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PersistenceService {
    void persistRecordBatch(TransferBatch transfer, Optional<CollaborationContext> collaborationContext);

    List<String> updateMetadata(List<RecordMetadata> recordMetadata, List<String> recordsId, Map<String, String> recordsIdMap, Optional<CollaborationContext> collaborationContext);

    Map<String, String> patchRecordsMetadata(Map<RecordMetadata, JsonPatch> jsonPatchPerRecord, Optional<CollaborationContext> collaborationContext);
}
