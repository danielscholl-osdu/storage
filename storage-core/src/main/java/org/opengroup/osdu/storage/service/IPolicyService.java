package org.opengroup.osdu.storage.service;

import org.opengroup.osdu.storage.model.policy.PolicyResponse;
import org.opengroup.osdu.storage.model.policy.StoragePolicy;

public interface IPolicyService {

    PolicyResponse evaluatePolicy(StoragePolicy policy);
}
