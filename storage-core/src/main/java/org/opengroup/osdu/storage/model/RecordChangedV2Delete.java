package org.opengroup.osdu.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.opengroup.osdu.core.common.model.indexer.DeletionType;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class RecordChangedV2Delete extends RecordChangedV2 {
    private DeletionType deletionType;

}
