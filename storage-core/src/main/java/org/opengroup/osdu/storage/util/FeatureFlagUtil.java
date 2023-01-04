package org.opengroup.osdu.storage.util;
import org.opengroup.osdu.core.common.feature.FeatureFlag;
import org.opengroup.osdu.core.common.feature.FeatureFlagFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
@Component
public class FeatureFlagUtil {
    @Value("${PARTITION_API}")
    private String partitionAPIEndpoint;
    @Autowired
    private FeatureFlagFactory featureFlagFactory;
    private FeatureFlag featureFlag;
    @PostConstruct
    public void init() {
        featureFlag = featureFlagFactory.create(partitionAPIEndpoint);
    }
    public boolean isFeatureEnabled(String featureName, String dataPartitionId) {
        return featureFlag.isEnabled(featureName, dataPartitionId);
    }
    public boolean isFeatureDisabled(String featureName, String dataPartitionId) {
        return featureFlag.isDisabled(featureName, dataPartitionId);
    }
}
