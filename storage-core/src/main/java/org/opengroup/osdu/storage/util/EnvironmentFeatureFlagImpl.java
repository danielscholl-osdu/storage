package org.opengroup.osdu.storage.util;

import org.springframework.beans.factory.annotation.Value;

public class EnvironmentFeatureFlagImpl implements ICollaborationFeatureFlag {
    @Value("${collaborations-enabled:false}")
    private boolean isCollaborationEnabled;

    @Override
    public boolean isFeatureEnabled(String... featureParameters) {
        return featureParameters[0] == "collaborations-enabled" ? isCollaborationEnabled == true : false;
    }

}
