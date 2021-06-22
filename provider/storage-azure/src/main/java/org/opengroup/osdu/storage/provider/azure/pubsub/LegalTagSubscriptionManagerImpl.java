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
import org.opengroup.osdu.storage.provider.interfaces.ILegalTagSubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.inject.Named;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Component
public class LegalTagSubscriptionManagerImpl implements ILegalTagSubscriptionManager {

    private final static Logger LOGGER = LoggerFactory.getLogger(LegalTagSubscriptionManagerImpl.class);
    @Autowired
    private LegalTagSubscriptionClientFactory legalTagSubscriptionClientFactory;
    @Autowired
    private LegalComplianceChangeServiceAzureImpl legalComplianceChangeServiceAzure;
    @Autowired
    @Named("LEGAL_SERVICE_BUS_TOPIC")
    private String legalServiceBusTopic;

    @Autowired
    @Named("LEGAL_SERVICE_BUS_TOPIC_SUBSCRIPTION")
    private String legalServiceBusTopicSubscription;

    @Autowired
    @Named("EXECUTOR-N-THREADS")
    private String nThreads;

    @Autowired
    @Named("MAX_CONCURRENT_CALLS")
    private String maxConcurrentCalls;

    @Autowired
    @Named("MAX_LOCK_RENEW")
    private String maxLockRenew;

    @Autowired
    private ITenantFactory tenantFactory;

    @Override
    public void subscribeLegalTagsChangeEvent() {
        List<String> tenantList = tenantFactory.listTenantInfo().stream().map(TenantInfo::getDataPartitionId)
                .collect(Collectors.toList());
        ExecutorService executorService = Executors
                .newFixedThreadPool(Integer.parseUnsignedInt(nThreads));
        for (String partition : tenantList) {
            try {
                SubscriptionClient subscriptionClient = this
                        .legalTagSubscriptionClientFactory
                        .getSubscriptionClient(partition, legalServiceBusTopic, legalServiceBusTopicSubscription);
                registerMessageHandler(subscriptionClient, executorService);
            } catch (Exception e) {
                LOGGER.error("Error while creating or registering subscription client", e);
            }
        }
    }

    private void registerMessageHandler(SubscriptionClient subscriptionClient, ExecutorService executorService) {
        try {
            LegalTagSubscriptionMessageHandler messageHandler = new LegalTagSubscriptionMessageHandler(subscriptionClient, legalComplianceChangeServiceAzure);
            subscriptionClient.registerMessageHandler(
                    messageHandler,
                    new MessageHandlerOptions(Integer.parseUnsignedInt(maxConcurrentCalls),
                            false,
                            Duration.ofSeconds(Integer.parseUnsignedInt(maxLockRenew)),
                            Duration.ofSeconds(1)
                    ),
                    executorService);

        } catch (InterruptedException | ServiceBusException e) {
            LOGGER.error("Error registering message handler {}", e.getMessage(), e);
        }
    }
}
