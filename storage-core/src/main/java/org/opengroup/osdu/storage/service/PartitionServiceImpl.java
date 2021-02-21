package org.opengroup.osdu.storage.service;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.partition.*;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(value = "management.policy.enabled", havingValue = "true", matchIfMissing = false)
public class PartitionServiceImpl implements IPartitionService {

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private IPartitionFactory factory;

    @Autowired
    private IServiceAccountJwtClient tokenService;

    @Override
    public PartitionInfo getPartition(String partitionId) {
        try {
            this.headers.put(DpsHeaders.AUTHORIZATION, this.tokenService.getIdToken(this.headers.getPartitionId()));

            IPartitionProvider serviceClient = this.factory.create(this.headers);
            PartitionInfo partitionInfo = serviceClient.get(partitionId);
            return partitionInfo;
        } catch (PartitionException e) {
            throw new AppException(HttpStatus.SC_FORBIDDEN, "Service unavailable", String.format("Error getting partition info for data-partition: %s", partitionId), e);
        }
    }
}

