package org.opengroup.osdu.storage.service;

import org.opengroup.osdu.core.common.partition.PartitionInfo;

public interface IPartitionService {
    PartitionInfo getPartition(String partitionId);
}
