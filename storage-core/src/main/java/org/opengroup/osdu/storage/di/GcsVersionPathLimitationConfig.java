package org.opengroup.osdu.storage.di;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gcs.version.paths")
public class GcsVersionPathLimitationConfig {

    private int maxLimit = 2000;

    private String featureName = "enforce_gcsVersionPathsLimit_enabled";
}
