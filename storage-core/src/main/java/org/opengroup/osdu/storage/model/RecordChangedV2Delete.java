package org.opengroup.osdu.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.indexer.DeletionType;
import org.opengroup.osdu.core.common.model.indexer.OperationType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecordChangedV2Delete extends RecordChangedV2 {
    private DeletionType deletionType;

    public RecordChangedV2Delete(String recordId, Long version, String modifiedBy, String kind, DeletionType deletionType) {
        super(recordId, version, modifiedBy, kind, OperationType.delete);
        this.deletionType = deletionType;
    }
}
