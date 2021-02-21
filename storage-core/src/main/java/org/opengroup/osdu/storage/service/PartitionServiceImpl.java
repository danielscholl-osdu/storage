package org.opengroup.osdu.storage.provider.azure;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.azure.util.AzureServicePrincipleTokenService;
import org.opengroup.osdu.common.Validators;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.partition.IPartitionFactory;
import org.opengroup.osdu.core.common.partition.IPartitionProvider;
import org.opengroup.osdu.core.common.partition.PartitionException;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.storage.service.IPartitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PartitionServiceImpl implements IPartitionService {

    @Autowired
    private IPartitionFactory partitionFactory;

    @Autowired
    private AzureServicePrincipleTokenService tokenService;

    /**
     * Get partition info.
     *
     * @param partitionId Partition Id
     * @return Partition info
     */
    @Override
    public PartitionInfo getPartition(String partitionId) {
        Validators.checkNotNullAndNotEmpty(partitionId, "partitionId");

        try {
            IPartitionProvider serviceClient = getServiceClient();
            PartitionInfo partitionInfo = serviceClient.get(partitionId);

            return partitionInfo;
        } catch (PartitionException e) {
            throw new AppException(HttpStatus.SC_FORBIDDEN, "Service unavailable", String.format("Error getting partition info for data-partition: %s", partitionId), e);
        }
    }

    /**
     * Get Service client for Partition Service.
     *
     * @return PartitionServiceClient
     */
    private IPartitionProvider getServiceClient() {
        DpsHeaders dpsHeaders = new DpsHeaders();
        dpsHeaders.put(DpsHeaders.AUTHORIZATION, "Bearer " + this.tokenService.getAuthorizationToken());
        return this.partitionFactory.create(dpsHeaders);
    }
}

