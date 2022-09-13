/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.messaging.jobs;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagConsistencyValidator;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.gcp.oqm.driver.OqmDriver;
import org.opengroup.osdu.core.gcp.oqm.model.OqmDestination;
import org.opengroup.osdu.core.gcp.oqm.model.OqmMessageReceiver;
import org.opengroup.osdu.core.gcp.oqm.model.OqmSubscriber;
import org.opengroup.osdu.core.gcp.oqm.model.OqmSubscription;
import org.opengroup.osdu.core.gcp.oqm.model.OqmSubscriptionQuery;
import org.opengroup.osdu.core.gcp.oqm.model.OqmTopic;
import org.opengroup.osdu.storage.provider.gcp.messaging.config.MessagingConfigurationProperties;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadDpsHeaders;
import org.opengroup.osdu.storage.provider.gcp.messaging.thread.ThreadScopeContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Runs once on the service start. Fetches all tenants' oqm destinations for TOPIC existence. If exists - searches for pull SUBSCRIPTION existence. Creates
 * SUBSCRIPTION if doesn't exist. Then subscribe itself on SUBSCRIPTION.
 */

@Slf4j
@Component
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "oqmDriver")
@RequiredArgsConstructor
public class OqmSubscriberManager {

    private final MessagingConfigurationProperties configurationProperties;

    private final ITenantFactory tenantInfoFactory;
    private final OqmDriver driver;

    private final LegalTagConsistencyValidator legalTagConsistencyValidator;
    private final LegalComplianceChangeServiceGcpImpl legalComplianceChangeServiceGcp;
    private final ThreadDpsHeaders dpsHeaders;

    @PostConstruct
    void postConstruct() {
        log.info("OqmSubscriberManager bean constructed. Provisioning STARTED");

        //Get all Tenant infos
        for (TenantInfo tenantInfo : tenantInfoFactory.listTenantInfo()) {
            log.info("* OqmSubscriberManager on provisioning tenant {}:", tenantInfo.getDataPartitionId());

            log.info("* * OqmSubscriberManager on check for topic {} existence:", configurationProperties.getLegalTagsChangedTopicName());
            OqmTopic topic = driver.getTopic(configurationProperties.getLegalTagsChangedTopicName(), getDestination(tenantInfo)).orElse(null);
            if (topic == null) {
                log.info("* * OqmSubscriberManager on check for topic {} existence: ABSENT", configurationProperties.getLegalTagsChangedTopicName());
                continue;
            } else {
                log.info("* * OqmSubscriberManager on check for topic {} existence: PRESENT", configurationProperties.getLegalTagsChangedTopicName());
            }

            String legalTagsChangedSubscriptionName = configurationProperties.getLegalTagsChangedSubscriptionName() + "-" +tenantInfo.getDataPartitionId();

            log.info("* * OqmSubscriberManager on check for subscription {} existence:", legalTagsChangedSubscriptionName);

            OqmSubscriptionQuery query = OqmSubscriptionQuery.builder()
                    .namePrefix(legalTagsChangedSubscriptionName)
                    .subscriberable(true)
                    .build();

            OqmSubscription subscription = driver.listSubscriptions(topic, query, getDestination(tenantInfo)).stream().findAny().orElse(null);

            if (subscription == null) {
                log.info("* * OqmSubscriberManager on check for subscription {} existence: ABSENT. Will create.", legalTagsChangedSubscriptionName);

                OqmSubscription request = OqmSubscription.builder()
                    .topic(topic)
                    .name(legalTagsChangedSubscriptionName)
                    .build();

                subscription = driver.createAndGetSubscription(request, getDestination(tenantInfo));
            } else {
                log.info("* * OqmSubscriberManager on check for subscription {} existence: PRESENT", legalTagsChangedSubscriptionName);
            }

            log.info("* * OqmSubscriberManager on registering Subscriber for tenant {}, subscription {}", tenantInfo.getDataPartitionId(),
                legalTagsChangedSubscriptionName);
            registerSubscriber(tenantInfo, subscription);
            log.info("* * OqmSubscriberManager on provisioning for tenant {}, subscription {}: Subscriber REGISTERED.", tenantInfo.getDataPartitionId(),
                subscription.getName());

            log.info("* OqmSubscriberManager on provisioning tenant {}: COMPLETED.", tenantInfo.getDataPartitionId());
        }

        log.info("OqmSubscriberManager bean constructed. Provisioning COMPLETED");
    }

    private void registerSubscriber(TenantInfo tenantInfo, OqmSubscription subscription) {
        OqmDestination destination = getDestination(tenantInfo);

        OqmMessageReceiver receiver = (oqmMessage, oqmAckReplier) -> {

            String pubsubMessage = oqmMessage.getData();
            Map<String, String> headerAttributes = oqmMessage.getAttributes();
            log.debug(pubsubMessage + " " + headerAttributes + " " + oqmMessage.getId());

            boolean ackedNacked = false;
            try {
                dpsHeaders.setThreadContext(headerAttributes);
                LegalTagChangedProcessing legalTagChangedProcessing =
                    new LegalTagChangedProcessing(legalTagConsistencyValidator, legalComplianceChangeServiceGcp, dpsHeaders);
                legalTagChangedProcessing.process(oqmMessage);
                log.info("OQM message handling for tenant {} topic {} subscription {}. ACK. Message: -data: {}, attributes: {}",
                    dpsHeaders.getPartitionId(),
                    configurationProperties.getLegalTagsChangedTopicName(),
                    configurationProperties.getLegalTagsChangedSubscriptionName(),
                    pubsubMessage,
                    StringUtils.join(headerAttributes)
                );
                oqmAckReplier.ack();
                ackedNacked = true;
            } catch (Exception e) {
                log.error("OQM message handling error for tenant {} topic {} subscription {}. Message: -data: {}, attributes: {}, error: {}",
                    dpsHeaders.getPartitionId(),
                    configurationProperties.getLegalTagsChangedTopicName(),
                    configurationProperties.getLegalTagsChangedSubscriptionName(),
                    pubsubMessage,
                    StringUtils.join(headerAttributes),
                    e
                );
            } finally {
                if (!ackedNacked) {
                    oqmAckReplier.nack();
                }
                ThreadScopeContextHolder.currentThreadScopeAttributes().clear();
            }
        };

        OqmSubscriber subscriber = OqmSubscriber.builder().subscription(subscription).messageReceiver(receiver).build();
        driver.subscribe(subscriber, destination);
        log.info("Just subscribed at topic {} subscription {} for tenant {}",
            subscription.getTopics().get(0).getName(), subscription.getName(), tenantInfo.getDataPartitionId());
    }

    private OqmDestination getDestination(TenantInfo tenantInfo) {
        return OqmDestination.builder().partitionId(tenantInfo.getDataPartitionId()).build();
    }

}
