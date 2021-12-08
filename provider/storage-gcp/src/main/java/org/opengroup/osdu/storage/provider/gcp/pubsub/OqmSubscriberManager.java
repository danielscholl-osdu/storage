/*
 * Copyright 2017-2020, Schlumberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.pubsub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.gcp.oqm.driver.OqmDriver;
import org.opengroup.osdu.core.gcp.oqm.model.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * Runs once on the service start.
 * Fetches all tenants' oqm destinations for TOPIC existence. If exists - searches for pull SUBSCRIPTION existence.
 * Creates SUBSCRIPTION if doesn't exist. Then subscribe itself on SUBSCRIPTION.
 */
@Slf4j
@Component
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "oqmDriver")
@RequiredArgsConstructor
public class OqmSubscriberManager {

    //TODO should be externalized to application.properties
    private final String topicName = "legaltags_changed";
    private final String subscriptionName = "storage-oqm-legaltags-changed";

    private final ITenantFactory tenantInfoFactory;
    private final OqmDriver driver;

    @PostConstruct
    void postConstruct() {
        log.info("OqmSubscriberManager bean constructed. Provisioning STARTED");

        //Get all Tenant infos
        for (TenantInfo tenantInfo : tenantInfoFactory.listTenantInfo()) {
            log.info("* OqmSubscriberManager on provisioning tenant {}:", tenantInfo.getDataPartitionId());

            log.info("* * OqmSubscriberManager on check for topic {} existence:", topicName);
            OqmTopic topic = driver.getTopic(topicName, getDestination(tenantInfo)).orElse(null);
            if (topic == null) {
                log.info("* * OqmSubscriberManager on check for topic {} existence: ABSENT", topicName);
                continue;
            } else {
                log.info("* * OqmSubscriberManager on check for topic {} existence: PRESENT", topicName);
            }

            log.info("* * OqmSubscriberManager on check for subscription {} existence:", subscriptionName);
            OqmSubscriptionQuery query = OqmSubscriptionQuery.builder().namePrefix(subscriptionName).subscriberable(true).build();
            OqmSubscription subscription = driver.listSubscriptions(topic, query, getDestination(tenantInfo)).stream().findAny().orElse(null);
            if (subscription == null) {
                log.info("* * OqmSubscriberManager on check for subscription {} existence: ABSENT. Will create.", subscriptionName);
                OqmSubscription request = OqmSubscription.builder().topic(topic).name(subscriptionName).build();
                subscription = driver.createAndGetSubscription(request, getDestination(tenantInfo));
            } else {
                log.info("* * OqmSubscriberManager on check for subscription {} existence: PRESENT", subscriptionName);
            }

            log.info("* * OqmSubscriberManager on registering Subscriber for tenant {}, subscription {}", tenantInfo.getDataPartitionId(), subscriptionName);
            registerSubscriber(tenantInfo, subscription);
            log.info("* * OqmSubscriberManager on provisioning for tenant {}, subscription {}: Subscriber REGISTERED.", tenantInfo.getDataPartitionId(), subscription.getName());

            log.info("* OqmSubscriberManager on provisioning tenant {}: COMPLETED.", tenantInfo.getDataPartitionId());
        }

        log.info("OqmSubscriberManager bean constructed. Provisioning COMPLETED");
    }

    private void registerSubscriber(TenantInfo tenantInfo, OqmSubscription subscription) {
        OqmDestination destination = getDestination(tenantInfo);

        OqmMessageReceiver receiver = (oqmMessage, oqmAckReplier) -> {

            String pubsubMessage = oqmMessage.getData();
            String notificationId = subscription.getName();
            Map<String, String> headerAttributes = oqmMessage.getAttributes();


            HttpResponse response;
            boolean ackedNacked = false;
            try {

                //TODO Implement real behaviour instead of stub. See below commented section. It is grabbed from core/PubSubEndlpoints...pushReceiver.receiveMessageFromHttpRequest(). Need for adapting classes used there for NON @RequestScope invocation
                /*
                LegalTagChangedCollection dto = (LegalTagChangedCollection)(new Gson()).fromJson(oqmMessage.getData(), LegalTagChangedCollection.class);
                LegalTagChangedCollection validDto = this.legalTagConsistencyValidator.checkLegalTagStatusWithLegalService(dto);
                this.legalComplianceChangeService.updateComplianceOnRecords(validDto, this.dpsHeaders);
                */
                log.info("OQM STUB message handling for tenant {} topic {} subscription {}. ACK. Message: -data: {}, attributes: {}",
                        topicName, subscriptionName, pubsubMessage, StringUtils.join(headerAttributes));
                oqmAckReplier.ack();
                ackedNacked = true;
            } catch (Exception e) {
                log.error("OQM STUB message handling error for tenant {} topic {} subscription {}. Message: -data: {}, attributes: {}",
                        topicName, subscriptionName, pubsubMessage, StringUtils.join(headerAttributes));
            }

            if (!ackedNacked) oqmAckReplier.nack();
        };

        OqmSubscriber subscriber = OqmSubscriber.builder().subscription(subscription).messageReceiver(receiver).build();
        driver.subscribe(subscriber, destination);
        log.info("Just subscribed at topic {} subscription {} for tenant {}",
                subscription.getTopics().get(0), subscription.getName(), tenantInfo.getDataPartitionId());
    }

    private OqmDestination getDestination(TenantInfo tenantInfo) {
        return OqmDestination.builder().partitionId(tenantInfo.getDataPartitionId()).build();
    }

}
