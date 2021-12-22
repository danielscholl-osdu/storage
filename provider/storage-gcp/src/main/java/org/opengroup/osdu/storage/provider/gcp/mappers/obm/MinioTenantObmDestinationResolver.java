/*
  Copyright 2021 Google LLC
  Copyright 2021 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.mappers.obm;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.partition.IPartitionProvider;
import org.opengroup.osdu.core.common.partition.PartitionException;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.core.common.partition.Property;
import org.opengroup.osdu.core.gcp.obm.driver.ObmDriverRuntimeException;
import org.opengroup.osdu.core.gcp.obm.driver.minio.MinioObmDestinationResolution;
import org.opengroup.osdu.core.gcp.obm.driver.minio.MinioObmDestinationResolver;
import org.opengroup.osdu.core.gcp.obm.persistence.ObmDestination;
import org.opengroup.osdu.core.gcp.osm.translate.TranslatorRuntimeException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * For RabbitMQ. Tenant Based OQM destination resolver
 */
@Component
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "obmDriver", havingValue = "minio")
@RequiredArgsConstructor
@Slf4j
public class MinioTenantObmDestinationResolver implements MinioObmDestinationResolver {

    private final MinioObmConfigurationProperties properties;

    private static final String ENDPOINT = ".endpoint";
    private static final String ACCESS_KEY = ".accessKey";
    private static final String SECRET_KEY = ".secretKey";

    private final IPartitionProvider partitionProvider;

    private final Map<String, MinioClient> minioCache = new HashMap<>();

    @Override
    public MinioObmDestinationResolution resolve(ObmDestination destination) {

        String partitionId = destination.getPartitionId();

        MinioClient minioClient = minioCache.get(partitionId);

        if (minioClient == null) {

            PartitionInfo partitionInfo;
            try {
                partitionInfo = partitionProvider.get(partitionId);
            } catch (PartitionException e) {
                throw new TranslatorRuntimeException(e, "Partition '%s' destination resolution issue", partitionId);
            }
            Map<String, Property> partitionProperties = partitionInfo.getProperties();

            String endpoint = getPartitionProperty(partitionId, partitionProperties, ENDPOINT);
            String accessKey = getPartitionProperty(partitionId, partitionProperties, ACCESS_KEY);
            String secretKey = getPartitionProperty(partitionId, partitionProperties, SECRET_KEY);

            try {
                minioClient = MinioClient.builder()
                        .endpoint(endpoint)
                        .credentials(accessKey, secretKey)
                        .build();

                minioCache.put(partitionId, minioClient);

            } catch (Exception e) {
                throw new ObmDriverRuntimeException("MiniIO Client", e);
            }
        }
        return MinioObmDestinationResolution.builder()
                .client(minioClient)
                .build();
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
