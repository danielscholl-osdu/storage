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
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.opengroup.osdu.storage.provider.azure.di.PubSubConfig;
import org.opengroup.osdu.storage.provider.azure.interfaces.ILegalTagSubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(value = "azure.feature.legaltag-compliance-update.enabled", havingValue = "true", matchIfMissing = false)
public class LegalTagSubscriptionManagerImpl implements ILegalTagSubscriptionManager {

    private final static Logger LOGGER = LoggerFactory.getLogger(LegalTagSubscriptionManagerImpl.class);
    @Autowired
    private LegalTagSubscriptionClientFactory legalTagSubscriptionClientFactory;
    @Autowired
    private LegalComplianceChangeUpdate legalComplianceChangeUpdate;
    @Autowired
    private PubSubConfig pubSubConfig;
    @Autowired
    private AzureBootstrapConfig azureBootstrapConfig;
    @Autowired
    private ITenantFactory tenantFactory;

    @Override
    public void subscribeLegalTagsChangeEvent() {

        List<String> tenantList = tenantFactory.listTenantInfo().stream().map(TenantInfo::getDataPartitionId)
                .collect(Collectors.toList());
        ExecutorService executorService = Executors
                .newFixedThreadPool(Integer.parseUnsignedInt(pubSubConfig.getSbExecutorThreadPoolSize()));
        for (String partition : tenantList) {
            try {
                SubscriptionClient subscriptionClient = this
                        .legalTagSubscriptionClientFactory
                        .getSubscriptionClient(partition, pubSubConfig.getLegalServiceBusTopic(), pubSubConfig.getLegalServiceBusTopicSubscription());
                registerMessageHandler(subscriptionClient, executorService);
            } catch (InterruptedException | ServiceBusException e) {
                LOGGER.error("Error while creating or registering subscription client {}", e.getMessage(), e);
            } catch (Exception e) {
                LOGGER.error("Error while creating or registering subscription client {}", e.getMessage(), e);
            }
        }
    }

    private void registerMessageHandler(SubscriptionClient subscriptionClient, ExecutorService executorService) throws ServiceBusException, InterruptedException {
        LegalTagSubscriptionMessageHandler messageHandler = new LegalTagSubscriptionMessageHandler(subscriptionClient, legalComplianceChangeUpdate);
        subscriptionClient.registerMessageHandler(
                messageHandler,
                new MessageHandlerOptions(Integer.parseUnsignedInt(pubSubConfig.getMaxConcurrentCalls()),
                        false,
                        Duration.ofSeconds(Integer.parseUnsignedInt(pubSubConfig.getMaxLockRenewDurationInSeconds())),
                        Duration.ofSeconds(1)
                ),
                executorService);
    }
}
