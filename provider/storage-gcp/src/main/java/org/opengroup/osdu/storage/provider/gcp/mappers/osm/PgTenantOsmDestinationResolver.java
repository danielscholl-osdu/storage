package org.opengroup.osdu.storage.provider.gcp.mappers.osm;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.partition.IPartitionProvider;
import org.opengroup.osdu.core.common.partition.PartitionException;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.core.common.partition.Property;
import org.opengroup.osdu.core.gcp.osm.model.Destination;
import org.opengroup.osdu.core.gcp.osm.translate.TranslatorRuntimeException;
import org.opengroup.osdu.core.gcp.osm.translate.postgresql.PgDestinationResolution;
import org.opengroup.osdu.core.gcp.osm.translate.postgresql.PgDestinationResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * Resolves Destination.partitionId into info needed by Postgres to address requests to a relevant DB server.
 *
 * @author Rostislav_Dublin
 * @since 15.09.2021
 */
@Component
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "osmDriver", havingValue = "postgres")
@RequiredArgsConstructor
@Slf4j
public class PgTenantOsmDestinationResolver implements PgDestinationResolver {

    private final IPartitionProvider partitionProvider;

    private final PgOsmConfigurationProperties properties;

    private final static String DATASOURCE = ".datasource.";
    private final static String URL = DATASOURCE.concat("url");
    private final static String USERNAME = DATASOURCE.concat("username");
    private final static String PASSWORD = DATASOURCE.concat("password");

    private final static String DRIVER_CLASS_NAME = "org.postgresql.Driver";

    final private Map<String, DataSource> dataSourceCache = new HashMap<>();

    @Override
    public PgDestinationResolution resolve(Destination destination) {

        String partitionId = destination.getPartitionId();

        //noinspection SwitchStatementWithTooFewBranches
        switch (partitionId) {
            default:
                DataSource dataSource = dataSourceCache.get(partitionId);
                if (dataSource == null || (dataSource instanceof HikariDataSource && ((HikariDataSource) dataSource).isClosed())) {
                    synchronized (dataSourceCache) {
                        dataSource = dataSourceCache.get(partitionId);
                        if (dataSource == null || (dataSource instanceof HikariDataSource && ((HikariDataSource) dataSource).isClosed())) {

                            PartitionInfo partitionInfo;
                            try {
                                partitionInfo = partitionProvider.get(destination.getPartitionId());
                            } catch (PartitionException e) {
                                throw new TranslatorRuntimeException(e, "Partition '%s' destination resolution issue", destination.getPartitionId());
                            }
                            Map<String, Property> partitionProperties = partitionInfo.getProperties();

                            String url = getPartitionProperty(partitionId, partitionProperties, URL);
                            String username = getPartitionProperty(partitionId, partitionProperties, USERNAME);
                            String password = getPartitionProperty(partitionId, partitionProperties, PASSWORD);

                            dataSource = DataSourceBuilder.create()
                                    .driverClassName(DRIVER_CLASS_NAME)
                                    .url(url)
                                    .username(username)
                                    .password(password)
                                    .build();

                            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                            hikariDataSource.setMaximumPoolSize(properties.getMaximumPoolSize());
                            hikariDataSource.setMinimumIdle(properties.getMinimumIdle());
                            hikariDataSource.setIdleTimeout(properties.getIdleTimeout());
                            hikariDataSource.setMaxLifetime(properties.getMaxLifetime());
                            hikariDataSource.setConnectionTimeout(properties.getConnectionTimeout());

                            dataSourceCache.put(partitionId, dataSource);
                        }
                    }
                }

                return PgDestinationResolution.builder()
                        .datasource(dataSource)
                        .build();
        }
    }

    @PreDestroy
    @Override
    public void close() {
        log.info("On pre-destroy. {} DataSources to shutdown", dataSourceCache.size());
        for (DataSource dataSource : dataSourceCache.values()) {
            if (dataSource instanceof HikariDataSource && !((HikariDataSource) dataSource).isClosed()) {
                ((HikariDataSource) dataSource).close();
            }
        }
    }

    private String getPartitionProperty(String partitionId, Map<String, Property> partitionProperties, String propertyName) {
        String fullName = properties.getPartitionPropertiesPrefix().concat(propertyName);
        return Optional.ofNullable(partitionProperties.get(fullName)).map(Property::getValue).map(Object::toString)
                .orElseThrow(() ->
                        new TranslatorRuntimeException(null,
                                "Partition '%s' Postgres OSM destination resolution configuration issue. Property '%s' is not provided in PartitionInfo.",
                                partitionId, fullName));
    }
}
