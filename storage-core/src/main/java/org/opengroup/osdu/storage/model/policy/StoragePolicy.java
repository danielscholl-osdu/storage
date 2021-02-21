package org.opengroup.osdu.storage.model.policy;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StoragePolicy {

    private String policyId;

    private PolicyInput input;
}
