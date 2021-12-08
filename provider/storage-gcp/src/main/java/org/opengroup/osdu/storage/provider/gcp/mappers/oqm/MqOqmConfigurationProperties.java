package org.opengroup.osdu.storage.provider.gcp.mappers.oqm;

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
@ConditionalOnProperty(name = "oqmDriver", havingValue = "rabbitmq")
@ConfigurationProperties(prefix = "oqm.rabbitmq")
@Getter
@Setter
public class MqOqmConfigurationProperties {

    private String partitionPropertiesPrefix = "storage.oqm.rabbitmq";

}
