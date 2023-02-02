package org.opengroup.osdu.storage.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.indexer.OperationType;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecordChangedV2 {
    private String id;
    private Long version;
    private String modifiedBy;
    private String kind;
    private OperationType op;

    /**
     * This specifies the changes that have been made to the record
     * e.g. "data" "data metadata" "data metadata+" "metadata-" ...
     */
    private String recordBlocks;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder.Default
    private String previousVersionKind = null;

    public RecordChangedV2(String id, Long version, String modifiedBy, String kind, OperationType operationType) {
        this.id = id;
        this.version = version;
        this.modifiedBy = modifiedBy;
        this.kind = kind;
        this.op = operationType;
    }


}
