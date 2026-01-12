package org.opengroup.osdu.storage.di;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="schema.endpoints")
@Getter
@Setter
public class SchemaEndpointsConfig {
  private boolean disabled = false;
}
