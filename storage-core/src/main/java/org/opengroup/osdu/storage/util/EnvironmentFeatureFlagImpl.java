package org.opengroup.osdu.storage.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentFeatureFlagImpl implements ICollaborationFeatureFlag {
    @Value("${collaborations-enabled:false}")
    private boolean isCollaborationEnabled;

    @Override
    public boolean isFeatureEnabled(String... featureParameters) {
        return featureParameters[0] == "collaborations-enabled" ? isCollaborationEnabled == true : false;
    }

    @Bean(name = "applicationPropertiesFeatureFlag")
    @ConditionalOnProperty(prefix = "collaboration", name = "featureFlag", havingValue = "appProperty")
    public ICollaborationFeatureFlag environmentFeatureFlag() {
        return new EnvironmentFeatureFlagImpl();
    }
}
