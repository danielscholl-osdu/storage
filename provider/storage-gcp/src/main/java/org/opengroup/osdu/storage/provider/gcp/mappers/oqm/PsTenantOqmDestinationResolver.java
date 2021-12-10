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
package org.opengroup.osdu.storage.provider.gcp.mappers.oqm;

import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.gcp.oqm.driver.OqmDriverRuntimeException;
import org.opengroup.osdu.core.gcp.oqm.driver.pubsub.PsOqmDestinationResolution;
import org.opengroup.osdu.core.gcp.oqm.driver.pubsub.PsOqmDestinationResolver;
import org.opengroup.osdu.core.gcp.oqm.model.OqmDestination;
import org.opengroup.osdu.storage.provider.gcp.util.GcpAppServiceConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.threeten.bp.Duration;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * For GCP PubSub. Tenant Based OQM destination resolver
 */
@Component
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "oqmDriver", havingValue = "pubsub")
@Slf4j
@RequiredArgsConstructor
public class PsTenantOqmDestinationResolver implements PsOqmDestinationResolver {

    private Map<OqmDestination, TopicAdminClient> topicClientCache = new HashMap<>();
    private Map<OqmDestination, SubscriptionAdminClient> subscriptionClientCache = new HashMap<>();

    private final ITenantFactory tenantInfoFactory;

    private static final RetrySettings RETRY_SETTINGS = RetrySettings.newBuilder()
            .setTotalTimeout(Duration.ofSeconds(10))
            .setInitialRetryDelay(Duration.ofMillis(5))
            .setRetryDelayMultiplier(2)
            .setMaxRetryDelay(Duration.ofSeconds(3))
            .setInitialRpcTimeout(Duration.ofSeconds(10))
            .setRpcTimeoutMultiplier(2)
            .setMaxRpcTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public PsOqmDestinationResolution resolve(OqmDestination destination) {
        TenantInfo ti = tenantInfoFactory.getTenantInfo(destination.getPartitionId());
        String partitionId = destination.getPartitionId();

        String topicProjectId = ti.getProjectId();
        String subscriptionProjectId = ti.getProjectId();

        TopicAdminClient tac = topicClientCache.get(destination);
        if (tac == null) {
            try {
                TopicAdminSettings tas = TopicAdminSettings.newBuilder().build();
                tac = TopicAdminClient.create(tas);
                topicClientCache.put(destination, tac);
            } catch (IOException e) {
                throw new OqmDriverRuntimeException("PsOqmDestinationResolution#resolve TopicAdminClient", e);
            }
        }

        SubscriptionAdminClient sac = subscriptionClientCache.get(destination);
        if (sac == null) {
            try {
                sac = SubscriptionAdminClient.create();
                subscriptionClientCache.put(destination, sac);
            } catch (IOException e) {
                throw new OqmDriverRuntimeException("PsOqmDestinationResolution#resolve SubscriptionAdminClient", e);
            }
        }

        return PsOqmDestinationResolution.builder()
                .servicesProjectId(topicProjectId)
                .dataProjectId(subscriptionProjectId)
                .topicAdminClient(tac)
                .subscriptionAdminClient(sac)
                .build();
    }

    @PreDestroy
    public void shutdown() {
        log.info("On pre-destroy. {} topic client(s) & {} subscription clients to shutdown",
                topicClientCache.size(), subscriptionClientCache.size());
        for (TopicAdminClient tac : topicClientCache.values()) {
            tac.shutdown();
        }
        for (SubscriptionAdminClient sac : subscriptionClientCache.values()) {
            sac.shutdown();
        }
    }
}
