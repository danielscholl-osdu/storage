// Copyright © Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.provider.azure.pubsub;

import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.storage.provider.azure.di.ServiceBusConfig;
import org.opengroup.osdu.storage.provider.azure.interfaces.ISubscriptionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplaySubscriptionRegistry implements ISubscriptionRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ReplaySubscriptionRegistry.class);
    private static final String SUBSCRIPTION_TYPE = "Replay";

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private ReplayMessageHandler replayMessageHandler;

    @Autowired
    private ServiceBusConfig serviceBusConfig;

    @Autowired
    private ITenantFactory tenantFactory;

    @Value("${feature.replay.enabled:false}")
    private Boolean replayEnabled;

    @Value("${replay.subscription.scheduler.enabled:true}")
    private Boolean schedulerEnabled;

    @Override
    public void subscribeToEvents() {
        subscriptionManager.subscribeToEvents(this);
    }

    @Override
    public Object getMessageHandler() {
        return replayMessageHandler;
    }

    @Override
    public List<String> getCurrentTenantList() {
        try {
            // For async context, we may need to handle ThreadScope differently
            return tenantFactory.listTenantInfo().stream()
                .filter(tenant -> tenant != null && tenant.getDataPartitionId() != null)
                .map(TenantInfo::getDataPartitionId)
                .filter(id -> !id.trim().isEmpty())
                .collect(Collectors.toList());
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("ThreadScope")) {
                logger.warn("ThreadScope not available in async context, returning empty tenant list");
                return List.of();
            }
            logger.error("Error retrieving tenant list: {}", e.getMessage(), e);
            return List.of();
        } catch (Exception e) {
            logger.error("Error retrieving tenant list: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public String getSubscriptionType() {
        return SUBSCRIPTION_TYPE;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(replayEnabled);
    }

    @Override
    public boolean isSchedulerEnabled() {
        return Boolean.TRUE.equals(replayEnabled) && Boolean.TRUE.equals(schedulerEnabled);
    }

    @Override
    public String getFeatureFlagProperty() {
        return "feature.replay.enabled";
    }

    @Override
    public String getSchedulerPropertyPrefix() {
        return "replay.subscription";
    }

    @Override
    public String getTopic() {
        return serviceBusConfig.getReplayTopic();
    }

    @Override
    public String getSubscription() {
        return serviceBusConfig.getReplaySubscription();
    }

    @Override
    public Set<String> getActiveSubscriptions() {
        return subscriptionManager.getActiveSubscriptions(SUBSCRIPTION_TYPE);
    }
}