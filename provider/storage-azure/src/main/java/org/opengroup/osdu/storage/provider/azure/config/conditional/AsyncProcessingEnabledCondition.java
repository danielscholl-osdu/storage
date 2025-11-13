package org.opengroup.osdu.storage.provider.azure.config.conditional;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class AsyncProcessingEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        boolean replay = env.getProperty("feature.replay.enabled", Boolean.class, false);
        boolean legalTagCompliance = env.getProperty("azure.feature.legaltag-compliance-update.enabled", Boolean.class, false);
        return replay || legalTagCompliance;
    }
}
