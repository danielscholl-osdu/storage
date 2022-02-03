package org.opengroup.osdu.storage.opa.service;

import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import java.util.List;
import java.util.Map;

public interface IOPAService {
    List<ValidationOutputRecord> validateRecordsCreationOrUpdate(List<Record> inputRecords, Map<String, RecordMetadata> existingRecords);
}
