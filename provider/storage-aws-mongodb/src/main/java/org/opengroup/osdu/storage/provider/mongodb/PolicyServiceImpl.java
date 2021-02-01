package org.opengroup.osdu.storage.provider.mongodb;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.model.policy.PolicyResponse;
import org.opengroup.osdu.storage.model.policy.StoragePolicy;
import org.opengroup.osdu.storage.service.IPolicyService;
import org.springframework.stereotype.Service;

@Service
public class PolicyServiceImpl implements IPolicyService {
    @Override
    public PolicyResponse evaluatePolicy(StoragePolicy policy) {
        throw new AppException(HttpStatus.SC_NOT_IMPLEMENTED, "Not Implemented", "Policy service not implemented yet");
    }
}
