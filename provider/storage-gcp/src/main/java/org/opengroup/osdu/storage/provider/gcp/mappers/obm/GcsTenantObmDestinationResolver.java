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

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.gcp.obm.driver.gcs.GcsObmDestinationResolution;
import org.opengroup.osdu.core.gcp.obm.driver.gcs.GcsObmDestinationResolver;
import org.opengroup.osdu.core.gcp.obm.persistence.ObmDestination;
import org.opengroup.osdu.core.gcp.oqm.driver.OqmDriverRuntimeException;
import org.opengroup.osdu.storage.provider.gcp.util.GcpAppServiceConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * For GCP PubSub. Tenant Based OQM destination resolver
 */
@Component
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "obmDriver", havingValue = "gcs")
@Slf4j
@RequiredArgsConstructor
public class GcsTenantObmDestinationResolver implements GcsObmDestinationResolver {

    private final Map<ObmDestination, Storage> storageCache = new HashMap<>();

    private final ITenantFactory tenantInfoFactory;
    private final GcpAppServiceConfig config;

    @Override
    public GcsObmDestinationResolution resolve(ObmDestination destination) {
        String partitionId = destination.getPartitionId();
        TenantInfo ti = tenantInfoFactory.getTenantInfo(partitionId);

        //noinspection SwitchStatementWithTooFewBranches

        Storage storage = storageCache.get(destination);
        if (storage == null) {
            try {
                storage = StorageOptions.newBuilder().setProjectId(ti.getProjectId()).build().getService();

                storageCache.put(destination, storage);
            } catch (Exception e) {
                throw new OqmDriverRuntimeException("GcsObmDestinationResolution#resolve Storage", e);
            }
        }

        return GcsObmDestinationResolution.builder()
                .dataProjectId(ti.getProjectId())
                .storage(storage)
                .build();
    }

    @PreDestroy
    public void shutdown() {
        log.info("On pre-destroy");
    }
}
