package org.opengroup.osdu.storage.model.policy;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.storage.Record;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
public class PolicyInput {
    private Enum operation;

    private List<String> groups;

    private Record record;

    private Set<String> legalTags;
}
