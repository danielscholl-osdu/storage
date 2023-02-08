package org.opengroup.osdu.storage.service;

import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


@Service
public class PatchRecordsServiceImpl implements PatchRecordsService {

    @Override
    public PatchRecordsResponse patchRecords(List<String> recordIds, JsonPatch patchPayload, String user, Optional<CollaborationContext> collaborationContext) {
        //assuming input validation on allowed operations and allowed paths is done

        //TODO: additional validation on recordIds and metadata properties

        //get records from repository

        //TODO: validate entitlements (user access) and compliance constraints on records received

        //TODO implement common patch logic

        return null;
    }
}
