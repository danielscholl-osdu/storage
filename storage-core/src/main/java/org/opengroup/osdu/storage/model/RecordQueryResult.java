package org.opengroup.osdu.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordQueryResult {

    private String cursor;

    private List<RecordMetadata> results;
}
