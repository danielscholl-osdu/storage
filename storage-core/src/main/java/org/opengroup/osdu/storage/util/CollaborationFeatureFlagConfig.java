package org.opengroup.osdu.storage.util;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CollaborationFeatureFlagConfig {

    @Bean
    @ConditionalOnProperty(prefix = "collaboration", name = "featureFlag", havingValue = "dataPartition")
    public DataPartitionFeatureFlagImpl dataPartitionFeatureFlag() {
        System.out.println("dataPartitionBean");
        return new DataPartitionFeatureFlagImpl();
    }

    @Bean
    @ConditionalOnProperty(prefix = "collaboration", name = "featureFlag", havingValue = "appProperty", matchIfMissing = true)
    public EnvironmentFeatureFlagImpl environmentFeatureFlag() {
        System.out.println("appPropertyBean");
        return new EnvironmentFeatureFlagImpl();
    }
}
