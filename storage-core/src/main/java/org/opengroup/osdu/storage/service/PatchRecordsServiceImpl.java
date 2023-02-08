package org.opengroup.osdu.storage.service;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PatchRecordsServiceImpl implements PatchRecordsService {

    @Override
    public PatchRecordsResponse patchRecords(RecordBulkUpdateParam recordBulkUpdateParam, String user, Optional<CollaborationContext> collaborationContext) {
        //input validation


        //TODO implement common patch logic
        return null;
    }
}
