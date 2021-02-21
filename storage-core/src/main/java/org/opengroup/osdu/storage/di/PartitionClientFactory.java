package org.opengroup.osdu.storage.di;

import org.opengroup.osdu.core.common.partition.IPartitionFactory;
import org.opengroup.osdu.core.common.partition.PartitionAPIConfig;
import org.opengroup.osdu.core.common.partition.PartitionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AbstractFactoryBean;

public class PartitionClientFactory extends AbstractFactoryBean<IPartitionFactory>  {

    @Value("${PARTITION_API}")
    private String partitionAPIEndpoint;

    @Override
    public Class<?> getObjectType() {
        return IPartitionFactory.class;
    }

    @Override
    protected IPartitionFactory createInstance() throws Exception {
        PartitionAPIConfig apiConfig = PartitionAPIConfig.builder()
                .rootUrl(partitionAPIEndpoint)
                .build();
        return new PartitionFactory(apiConfig);
    }
}
