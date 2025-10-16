package org.opengroup.osdu.storage.service;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;

import java.util.List;
import java.util.Optional;

public interface IngestionService {

    TransferInfo createUpdateRecords(boolean skipDupes, List<Record> records, String currentUser, Optional<CollaborationContext> collaborationContext);
}
