package org.opengroup.osdu.storage.model.policy;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PolicyRequest {

    private String policyId;

    private StoragePolicy input;
}
