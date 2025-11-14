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

import com.microsoft.azure.servicebus.MessageHandlerOptions;
import com.microsoft.azure.servicebus.SubscriptionClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.opengroup.osdu.azure.servicebus.ISubscriptionClientFactory;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.storage.provider.azure.di.ServiceBusConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true")
@Component
public class ReplaySubscriptionManager {

    private final static Logger LOGGER = LoggerFactory.getLogger(ReplaySubscriptionManager.class);

    @Autowired
    private ServiceBusConfig serviceBusConfig;

    @Autowired
    private ITenantFactory tenantFactory;

    @Autowired
    private ReplayMessageHandler replayMessageHandler;

    @Autowired
    private ISubscriptionClientFactory subscriptionClientFactory;

    @Value("#{${replay.routingProperties}}")
    private Map<String, String> replayRoutingProperty;

    public void subscribeToEvents() {

        List<String> tenantList = tenantFactory.listTenantInfo().stream().map(TenantInfo::getDataPartitionId).
                                               collect(Collectors.toList());

        ExecutorService executorService = Executors
                .newFixedThreadPool(Integer.parseUnsignedInt(serviceBusConfig.getSbExecutorThreadPoolSize()));

        for (String partition : tenantList) {
            try {
                LOGGER.info("Subscribing to replay events for partition: {}", partition);
                SubscriptionClient subscriptionClient =
                        this.subscriptionClientFactory
                                .getClient(partition, replayRoutingProperty.get("topic"), replayRoutingProperty.get("topicSubscription"));

                registerMessageHandler(subscriptionClient, replayMessageHandler, executorService);
                LOGGER.info("Successfully subscribed to replay events for partition: {}", partition);
            } catch (Exception e) {
                LOGGER.error("Error while creating or registering replay topic subscription client {}", e.getMessage(), e);
            }
        }
    }

    private void registerMessageHandler(SubscriptionClient subscriptionClient, ReplayMessageHandler replayMessageHandler, ExecutorService executorService) throws ServiceBusException, InterruptedException {

        LOGGER.info("Registering Replay Message Handler");
        ReplaySubscriptionMessageHandler messageHandler = new ReplaySubscriptionMessageHandler(subscriptionClient, replayMessageHandler);

        subscriptionClient.registerMessageHandler(
                messageHandler,
                new MessageHandlerOptions(
                        Integer.parseUnsignedInt(serviceBusConfig.getMaxConcurrentCalls()),
                        false,
                        Duration.ofSeconds(Integer.parseUnsignedInt(serviceBusConfig.getMaxLockRenewDurationInSeconds())),
                        Duration.ofSeconds(1)
                ),
                executorService);

        LOGGER.info("Successfully registered Replay Message Handler.");
    }
}
