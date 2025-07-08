package org.opengroup.osdu.storage.provider.gcp.messaging.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true")
@ConfigurationProperties(prefix = "replay")
@Data
public class ReplayConfigurationProperties {
  private String deadLetterTopicName;
  private String deadLetterSubscriptionName;
}
