package org.opengroup.osdu.storage.provider.gcp.mappers.oqm;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.ClientParameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.partition.IPartitionProvider;
import org.opengroup.osdu.core.common.partition.PartitionException;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.core.common.partition.Property;
import org.opengroup.osdu.core.gcp.oqm.driver.OqmDriverRuntimeException;
import org.opengroup.osdu.core.gcp.oqm.driver.rabbitmq.MqOqmDestinationResolution;
import org.opengroup.osdu.core.gcp.oqm.model.OqmDestination;
import org.opengroup.osdu.core.gcp.osm.translate.TranslatorRuntimeException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * For RabbitMQ. Tenant Based OQM destination resolver
 *
 * @author Rostislav_Dublin
 * @since 09.11.2021
 */
@Component
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "oqmDriver", havingValue = "rabbitmq")
@RequiredArgsConstructor
@Slf4j
public class MqTenantOqmDestinationResolver implements org.opengroup.osdu.core.gcp.oqm.driver.rabbitmq.MqOqmDestinationResolver {

    private final MqOqmConfigurationProperties properties;

    //Compose names to get configuration properties from Partition
    private static final String AMQP = ".amqp.";
    private static final String AMQP_HOST = AMQP.concat("host");
    private static final String AMQP_PORT = AMQP.concat("port");
    private static final String AMQP_PATH = AMQP.concat("path");
    private static final String AMQP_USERNAME = AMQP.concat("username");
    private static final String AMQP_PASSWORD = AMQP.concat("password");

    private static final String ADMIN = ".admin.";
    private static final String ADMIN_SCHEMA = ADMIN.concat("schema");
    private static final String ADMIN_HOST = ADMIN.concat("host");
    private static final String ADMIN_PORT = ADMIN.concat("port");
    private static final String ADMIN_PATH = ADMIN.concat("path");
    private static final String ADMIN_USERNAME = ADMIN.concat("username");
    private static final String ADMIN_PASSWORD = ADMIN.concat("password");

    private final IPartitionProvider partitionProvider;

    private final Map<String, ConnectionFactory> amqpConnectionFactoryCache = new HashMap<>();
    private final Map<String, Client> httpClientCache = new HashMap<>();

    @Override
    public MqOqmDestinationResolution resolve(OqmDestination destination) {

        String partitionId = destination.getPartitionId();

        //noinspection SwitchStatementWithTooFewBranches
        switch (partitionId) {
            default:

                String virtualHost = "/";

                ConnectionFactory amqpFactory = amqpConnectionFactoryCache.get(partitionId);
                Client httpClient = httpClientCache.get(partitionId);

                if (amqpFactory == null || httpClient == null) {

                    PartitionInfo partitionInfo;
                    try {
                        partitionInfo = partitionProvider.get(partitionId);
                    } catch (PartitionException e) {
                        throw new TranslatorRuntimeException(e, "Partition '%s' destination resolution issue", destination.getPartitionId());
                    }
                    Map<String, Property> partitionProperties = partitionInfo.getProperties();

                    if (amqpFactory == null) {

                        String amqpHost = getPartitionProperty(partitionId, partitionProperties, AMQP_HOST);
                        String amqpPort = getPartitionProperty(partitionId, partitionProperties, AMQP_PORT);
                        String amqpPath = getPartitionProperty(partitionId, partitionProperties, AMQP_PATH);
                        String amqpUser = getPartitionProperty(partitionId, partitionProperties, AMQP_USERNAME);
                        String amqpPass = getPartitionProperty(partitionId, partitionProperties, AMQP_PASSWORD);

                        URI amqpUri;
                        try {
                            amqpUri = new URI("amqp", amqpUser + ":" + amqpPass, amqpHost, Integer.parseInt(amqpPort), amqpPath, null, null);
                            amqpFactory = new ConnectionFactory();
                            amqpFactory.setUri(amqpUri);
                            amqpConnectionFactoryCache.put(partitionId, amqpFactory);

                        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
                            throw new OqmDriverRuntimeException("RabbitMQ amqp URI and ConnectionFactory", e);
                        }
                    }

                    if (httpClient == null) {

                        String adminSchm = getPartitionProperty(partitionId, partitionProperties, ADMIN_SCHEMA);
                        String adminHost = getPartitionProperty(partitionId, partitionProperties, ADMIN_HOST);
                        String adminPort = getPartitionProperty(partitionId, partitionProperties, ADMIN_PORT);
                        String adminPath = getPartitionProperty(partitionId, partitionProperties, ADMIN_PATH);
                        String adminUser = getPartitionProperty(partitionId, partitionProperties, ADMIN_USERNAME);
                        String adminPass = getPartitionProperty(partitionId, partitionProperties, ADMIN_PASSWORD);

                        try {
                            URI httpUrl = new URI(adminSchm, null, adminHost, Integer.parseInt(adminPort), adminPath, null, null);
                            ClientParameters clientParameters = new ClientParameters().url(httpUrl.toURL())
                                    .username(adminUser).password(adminPass);

                            httpClient = new Client(clientParameters);
                            httpClientCache.put(partitionId, httpClient);

                        } catch (URISyntaxException | MalformedURLException e) {
                            throw new OqmDriverRuntimeException("RabbitMQ http(api) URI and Client", e);
                        }
                    }
                }
                return MqOqmDestinationResolution.builder()
                        .amqpFactory(amqpFactory)
                        .adminClient(httpClient)
                        .virtualHost(virtualHost)
                        .build();
        }
    }

    private String getPartitionProperty(String partitionId, Map<String, Property> partitionProperties, String propertyName) {
        String fullName = properties.getPartitionPropertiesPrefix().concat(propertyName);
        return Optional.ofNullable(partitionProperties.get(fullName)).map(Property::getValue).map(Object::toString)
                .orElseThrow(() -> new TranslatorRuntimeException(null,
                        "Partition '%s' RabbitMQ OQM destination resolution configuration issue. Property '%s' is not provided in PartitionInfo.",
                        partitionId, fullName));
    }

    @PreDestroy
    public void shutdown() {
        log.info("On pre-destroy.");
    }
}
