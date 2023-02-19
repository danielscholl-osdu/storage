package org.opengroup.osdu.storage.util.api;

import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.storage.model.RecordPatchOperation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PatchUtil {
    Map<String, JsonPatch> convertPatchOpsToJsonPatch(List<String> ids, List<RecordPatchOperation> patchOps, Optional<CollaborationContext> collaborationContext);
}
