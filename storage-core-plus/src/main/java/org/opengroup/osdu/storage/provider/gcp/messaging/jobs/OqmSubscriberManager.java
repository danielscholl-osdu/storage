/*
 *  Copyright 2020-2023 Google LLC
 *  Copyright 2020-2023 EPAM Systems, Inc
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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagConsistencyValidator;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;

import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.*;
import org.opengroup.osdu.storage.provider.gcp.messaging.config.MessagingConfigurationProperties;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadDpsHeaders;
import org.opengroup.osdu.storage.provider.gcp.messaging.thread.ThreadScopeContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Runs once on the service start. Fetches all tenants' oqm destinations for TOPIC existence. If exists - searches for pull SUBSCRIPTION existence. Creates
 * SUBSCRIPTION if doesn't exist. Then subscribe itself on SUBSCRIPTION.
 */

@Slf4j
@Component
@Scope(SCOPE_SINGLETON)
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
        log.debug("OqmSubscriberManager bean constructed. Provisioning STARTED.");

        //Get all Tenant infos
        for (TenantInfo tenantInfo : tenantInfoFactory.listTenantInfo()) {
            String dataPartitionId = tenantInfo.getDataPartitionId();
            String tagsChangedTopicName = configurationProperties.getLegalTagsChangedTopicName();
            log.debug("* OqmSubscriberManager on provisioning tenant {}:", dataPartitionId);

            log.debug("* * OqmSubscriberManager on check for topic {} existence:",
                tagsChangedTopicName);
            OqmTopic topic = driver.getTopic(tagsChangedTopicName, getDestination(tenantInfo)).orElse(null);
            if (topic == null) {
                log.error("* * OqmSubscriberManager on check for topic {} existence: ABSENT.",
                    tagsChangedTopicName);
                throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Required topic not exists.",
                    String.format(
                        "Required topic not exists. Create topic: %s for tenant: %s and restart service.",
                        tagsChangedTopicName, dataPartitionId
                    )
                );
            }

            String legalTagsChangedSubscriptionName = configurationProperties.getLegalTagsChangedSubscriptionName();

            log.debug("* * OqmSubscriberManager on check for subscription {} existence:", legalTagsChangedSubscriptionName);

            OqmSubscriptionQuery query = OqmSubscriptionQuery.builder()
                    .namePrefix(legalTagsChangedSubscriptionName)
                    .subscriberable(true)
                    .build();

            OqmSubscription subscription = driver.listSubscriptions(topic, query, getDestination(tenantInfo)).stream().findAny().orElse(null);

            if (subscription == null) {
                log.error("* * OqmSubscriberManager on check for subscription {} existence: ABSENT. Will create.", legalTagsChangedSubscriptionName);
                throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Required subscription not exists.",
                    String.format(
                        "Required subscription not exists. Create subscription: %s for tenant: %s and restart service.",
                        legalTagsChangedSubscriptionName,
                        dataPartitionId
                    )
                );
            } else {
                log.debug("* * OqmSubscriberManager on check for subscription {} existence: PRESENT.", legalTagsChangedSubscriptionName);
            }

            log.debug("* * OqmSubscriberManager on registering Subscriber for tenant {}, subscription {}",
                dataPartitionId,
                legalTagsChangedSubscriptionName);
            registerSubscriber(tenantInfo, subscription);
            log.debug("* * OqmSubscriberManager on provisioning for tenant {}, subscription {}: Subscriber REGISTERED.",
                dataPartitionId,
                subscription.getName());

            log.debug("* OqmSubscriberManager on provisioning tenant {}: COMPLETED.",
                dataPartitionId);
        }

        log.debug("OqmSubscriberManager bean constructed. Provisioning COMPLETED.");
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
                log.debug("OQM message handling for tenant {} topic {} subscription {}. ACK. Message: -data: {}, attributes: {}.",
                    dpsHeaders.getPartitionId(),
                    configurationProperties.getLegalTagsChangedTopicName(),
                    configurationProperties.getLegalTagsChangedSubscriptionName(),
                    pubsubMessage,
                    StringUtils.join(headerAttributes)
                );
                oqmAckReplier.ack();
                ackedNacked = true;
            } catch (Exception e) {
                log.error("OQM message handling error for tenant {} topic {} subscription {}. Message: -data: {}, attributes: {}, error: {}.",
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
        log.debug("Just subscribed at topic {} subscription {} for tenant {}.",
            subscription.getTopics().get(0).getName(), subscription.getName(), tenantInfo.getDataPartitionId());
    }

    private OqmDestination getDestination(TenantInfo tenantInfo) {
        return OqmDestination.builder().partitionId(tenantInfo.getDataPartitionId()).build();
    }

}
