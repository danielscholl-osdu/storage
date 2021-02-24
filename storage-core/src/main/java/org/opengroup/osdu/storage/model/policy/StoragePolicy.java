package org.opengroup.osdu.storage.model.policy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.storage.Record;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoragePolicy {

    private Enum operation;

    private List<String> groups;

    private Record record;
}
