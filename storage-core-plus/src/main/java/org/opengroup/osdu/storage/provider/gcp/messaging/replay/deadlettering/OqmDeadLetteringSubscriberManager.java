/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
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

package org.opengroup.osdu.storage.provider.gcp.messaging.replay.deadlettering;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.*;
import org.opengroup.osdu.storage.provider.gcp.messaging.config.ReplayConfigurationProperties;
import org.opengroup.osdu.storage.provider.gcp.messaging.replay.ReplayMessageService;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadDpsHeaders;
import org.opengroup.osdu.storage.provider.gcp.messaging.thread.ThreadScopeContextHolder;
import org.opengroup.osdu.storage.provider.gcp.messaging.util.OqmMessageValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope(SCOPE_SINGLETON)
@RequiredArgsConstructor
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true")
public class OqmDeadLetteringSubscriberManager {
  private final ITenantFactory tenantInfoFactory;
  private final OqmDriver driver;
  private final ThreadDpsHeaders dpsHeaders;
  private final ReplayMessageService replayMessageService;
  private final ReplayConfigurationProperties properties;

  @PostConstruct
  void postConstruct() {
    log.debug("OqmDeadLetteringSubscriberManager bean constructed. Provisioning STARTED.");
    String deadLetterTopicName = properties.getDeadLetterTopicName();
    String deadLetterSubscriptionName = properties.getDeadLetterSubscriptionName();
    checkParams(deadLetterTopicName, deadLetterSubscriptionName);

    // Get all Tenant infos
    for (TenantInfo tenantInfo : tenantInfoFactory.listTenantInfo()) {
      String dataPartitionId = tenantInfo.getDataPartitionId();

      log.debug("* OqmDeadLetteringSubscriberManager on provisioning tenant {}:", dataPartitionId);

      log.debug(
          "* * OqmDeadLetteringSubscriberManager on check for topic {} existence:",
          deadLetterTopicName);
      OqmTopic topic =
          driver.getTopic(deadLetterTopicName, getDestination(tenantInfo)).orElse(null);
      checkTopic(topic, deadLetterTopicName, dataPartitionId);

      log.debug(
          "* * OqmDeadLetteringSubscriberManager on check for subscription {} existence:",
          deadLetterSubscriptionName);

      OqmSubscriptionQuery query =
          OqmSubscriptionQuery.builder()
              .namePrefix(deadLetterSubscriptionName)
              .subscriberable(true)
              .build();

      OqmSubscription subscription =
          driver.listSubscriptions(topic, query, getDestination(tenantInfo)).stream()
              .findAny()
              .orElse(null);

      checkSubscription(subscription, deadLetterSubscriptionName, dataPartitionId);

      log.debug(
          "* * OqmDeadLetteringSubscriberManager on registering Subscriber for tenant {}, subscription {}",
          dataPartitionId,
          deadLetterSubscriptionName);
      registerSubscriber(tenantInfo, subscription);
      log.debug(
          "* * OqmDeadLetteringSubscriberManager on provisioning for tenant {}, subscription {}: Subscriber REGISTERED.",
          dataPartitionId,
          subscription.getName());

      log.debug(
          "* OqmDeadLetteringSubscriberManager on provisioning tenant {}: COMPLETED.",
          dataPartitionId);
    }

    log.debug("OqmDeadLetteringSubscriberManager bean constructed. Provisioning COMPLETED.");
  }

  private static void checkSubscription(OqmSubscription subscription, String deadLetterSubscriptionName, String dataPartitionId) {
    if (subscription == null) {
      log.error(
          "* * OqmDeadLetteringSubscriberManager on check for subscription {} existence: ABSENT.",
          deadLetterSubscriptionName);
      throw new AppException(
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Required subscription not exists.",
          String.format(
              "Required subscription not exists. Create subscription: %s for tenant: %s and restart service.",
              deadLetterSubscriptionName, dataPartitionId));
    } else {
      log.debug(
          "* * OqmDeadLetteringSubscriberManager on check for subscription {} existence: PRESENT.",
          deadLetterSubscriptionName);
    }
  }

  private static void checkTopic(OqmTopic topic, String deadLetterTopicName, String dataPartitionId) {
    if (topic == null) {
      log.error(
          "* * OqmDeadLetteringSubscriberManager on check for topic {} existence: ABSENT.",
          deadLetterTopicName);
      throw new AppException(
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Required topic not exists.",
          String.format(
              "Required topic not exists. Create topic: %s for tenant: %s and restart service.",
              deadLetterTopicName, dataPartitionId));
    }
  }

  private static void checkParams(String deadLetterTopicName, String deadLetterSubscriptionName) {
    if (deadLetterTopicName == null || deadLetterSubscriptionName == null) {
      throw new AppException(
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Required topic or subscription not exists.",
          String.format(
              "Required topic or subscription not exists in properties. Create property topic: %s or subscription: %s.",
              deadLetterTopicName, deadLetterSubscriptionName));
    }
  }

  private void registerSubscriber(TenantInfo tenantInfo, OqmSubscription subscription) {
    OqmDestination destination = getDestination(tenantInfo);

    OqmMessageReceiver receiver =
        (oqmMessage, oqmAckReplier) -> {
          if (!OqmMessageValidator.isValid(oqmMessage)) {
            log.error("Not valid event payload, event will not be processed.");
            oqmAckReplier.nack(false);
            return;
          }
          String pubSubMessage = oqmMessage.getData();
          Map<String, String> headerAttributes = oqmMessage.getAttributes();
          log.debug(
              "OqmDeadLetteringSubscriberManager {} {} {}",
              pubSubMessage,
              headerAttributes,
              oqmMessage.getId());

          boolean ackedNacked = false;
          try {
            dpsHeaders.setThreadContext(headerAttributes);
            DeadLetteringMessageProcessing deadLetteringMessageProcessing =
                new DeadLetteringMessageProcessing(replayMessageService, dpsHeaders);
            deadLetteringMessageProcessing.process(oqmMessage);
            log.debug(
                "OQM message handling for tenant {} topic {} subscription {}. ACK. Message = data: {}, attributes: {}.",
                dpsHeaders.getPartitionId(),
                properties.getDeadLetterTopicName(),
                properties.getDeadLetterSubscriptionName(),
                pubSubMessage,
                StringUtils.join(headerAttributes));
            oqmAckReplier.ack();
            ackedNacked = true;
          } catch (Exception e) {
            log.error(
                "OQM message handling error for tenant {} topic {} subscription {}. Message = data: {}, attributes: {}, error: {}.",
                dpsHeaders.getPartitionId(),
                properties.getDeadLetterTopicName(),
                properties.getDeadLetterSubscriptionName(),
                pubSubMessage,
                StringUtils.join(headerAttributes),
                e);
          } finally {
            if (!ackedNacked) {
              oqmAckReplier.nack(false);
            }
            ThreadScopeContextHolder.clearContext();
          }
        };

    OqmSubscriber subscriber =
        OqmSubscriber.builder()
            .subscription(subscription)
            .messageReceiver(receiver)
            .deadLetteringRequired(false)
            .build();
    driver.subscribe(subscriber, destination);
    log.debug(
        "Just subscribed at topic {} subscription {} for tenant {}.",
        subscription.getTopics().get(0).getName(),
        subscription.getName(),
        tenantInfo.getDataPartitionId());
  }

  private OqmDestination getDestination(TenantInfo tenantInfo) {
    return OqmDestination.builder().partitionId(tenantInfo.getDataPartitionId()).build();
  }
}
