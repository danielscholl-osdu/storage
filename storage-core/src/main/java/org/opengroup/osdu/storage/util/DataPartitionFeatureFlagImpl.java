package org.opengroup.osdu.storage.util;

import org.opengroup.osdu.core.common.feature.FeatureFlag;
import org.opengroup.osdu.core.common.feature.FeatureFlagFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class DataPartitionFeatureFlagImpl implements ICollaborationFeatureFlag {
    @Value("${PARTITION_API}")
    private String partitionAPIEndpoint;
    @Autowired
    private FeatureFlagFactory featureFlagFactory;

    private FeatureFlag featureFlag;

    @PostConstruct
    public void init() {
        featureFlag = featureFlagFactory.create(partitionAPIEndpoint);
    }

    @Override
    public boolean isFeatureEnabled(String... featureParameters) {
        return featureFlag.isEnabled(featureParameters[0], featureParameters[1]);
    }

    @Bean(name = "dataPartitionFeatureFlag")
    @ConditionalOnProperty(prefix = "collaboration", name = "featureFlag", havingValue = "dataPartition")
    public ICollaborationFeatureFlag dataPartitionFeatureFlag() {
        return new DataPartitionFeatureFlagImpl();
    }
}
