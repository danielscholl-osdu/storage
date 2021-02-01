package org.opengroup.osdu.storage.provider.mongodb;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.storage.service.IPartitionService;
import org.springframework.stereotype.Service;

@Service
public class PartitionServiceImpl implements IPartitionService {
    @Override
    public PartitionInfo getPartition(String partitionId) {
        throw new AppException(HttpStatus.SC_NOT_IMPLEMENTED, "Not Implemented", "Policy service not implemented yet");
    }
}
