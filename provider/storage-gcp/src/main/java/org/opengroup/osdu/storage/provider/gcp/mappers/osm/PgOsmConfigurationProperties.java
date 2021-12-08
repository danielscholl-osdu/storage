package org.opengroup.osdu.storage.provider.gcp.mappers.osm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author Rostislav_Dublin
 * @since 28.11.2021
 */

@Configuration
@ConditionalOnProperty(name = "osmDriver", havingValue = "postgres")
@ConfigurationProperties(prefix = "osm.postgres")
@Getter
@Setter
public class PgOsmConfigurationProperties {

    private String partitionPropertiesPrefix = "osm.postgres";

    private Integer maximumPoolSize = 40;
    private Integer minimumIdle = 0;
    private Integer idleTimeout = 30000;
    private Integer maxLifetime = 1800000;
    private Integer connectionTimeout = 30000;
}
