package org.opengroup.osdu.storage.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.opengroup.osdu.core.common.model.indexer.OperationType;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class RecordChangedV2 {
    private String id;
    private Long version;
    private String modifiedBy;
    private String kind;
    private OperationType operationType;

    /**
     * This specifies the changes that have been made to the record
     * e.g. "data" "data metadata" "data metadata+" "metadata-" ...
     */
    private String recordBlocks;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder.Default
    private String previousVersionKind = null;

}
