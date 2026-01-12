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

package org.opengroup.osdu.storage.provider.gcp.messaging.replay;

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
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadDpsHeaders;
import org.opengroup.osdu.storage.provider.gcp.messaging.thread.ThreadScopeContextHolder;
import org.opengroup.osdu.storage.provider.gcp.messaging.util.OqmMessageValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope(SCOPE_SINGLETON)
@RequiredArgsConstructor
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true")
public class OqmReplayApiSubscriberManager {
  private static final String TOPIC = "topic";
  private static final String TOPIC_SUBSCRIPTION = "topicSubscription";
  private final ITenantFactory tenantInfoFactory;
  private final OqmDriver driver;
  private final ThreadDpsHeaders dpsHeaders;
  private final ReplayMessageService replayMessageService;

  @Value("#{${replay.routingProperties}}")
  private Map<String, String> replayRoutingProperty;

  @PostConstruct
  void postConstruct() {
    log.debug("OqmReplayApiSubscriberManager bean constructed. Provisioning STARTED.");
    String replayRoutingTopicName = replayRoutingProperty.get(TOPIC);
    String replayRoutingPropertySubscriptionName = replayRoutingProperty.get(TOPIC_SUBSCRIPTION);
    checkParams(replayRoutingPropertySubscriptionName, replayRoutingTopicName);

    // Get all Tenant infos
    for (TenantInfo tenantInfo : tenantInfoFactory.listTenantInfo()) {
      String dataPartitionId = tenantInfo.getDataPartitionId();

      log.debug("* OqmReplayApiSubscriberManager on provisioning tenant {}:", dataPartitionId);

      log.debug(
          "* * OqmReplayApiSubscriberManager on check for topic {} existence:",
          replayRoutingTopicName);
      OqmTopic topic =
          driver.getTopic(replayRoutingTopicName, getDestination(tenantInfo)).orElse(null);
      checkTopic(topic, replayRoutingTopicName, dataPartitionId);

      log.debug(
          "* * OqmReplayApiSubscriberManager on check for subscription {} existence:",
          replayRoutingPropertySubscriptionName);

      OqmSubscriptionQuery query =
          OqmSubscriptionQuery.builder()
              .namePrefix(replayRoutingPropertySubscriptionName)
              .subscriberable(true)
              .build();

      OqmSubscription subscription =
          driver.listSubscriptions(topic, query, getDestination(tenantInfo)).stream()
              .findAny()
              .orElse(null);

      checkSubscription(subscription, replayRoutingPropertySubscriptionName, dataPartitionId);

      log.debug(
          "* * OqmReplayApiSubscriberManager on registering Subscriber for tenant {}, subscription {}",
          dataPartitionId,
          replayRoutingPropertySubscriptionName);
      registerSubscriber(tenantInfo, subscription);
      log.debug(
          "* * OqmReplayApiSubscriberManager on provisioning for tenant {}, subscription {}: Subscriber REGISTERED.",
          dataPartitionId,
          subscription.getName());

      log.debug(
          "* OqmReplayApiSubscriberManager on provisioning tenant {}: COMPLETED.", dataPartitionId);
    }

    log.debug("OqmReplayApiSubscriberManager bean constructed. Provisioning COMPLETED.");
  }

  private static void checkSubscription(OqmSubscription subscription, String replayRoutingPropertySubscriptionName, String dataPartitionId) {
    if (subscription == null) {
      log.error(
          "* * OqmReplayApiSubscriberManager on check for subscription {} existence: ABSENT.",
          replayRoutingPropertySubscriptionName);
      throw new AppException(
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Required subscription not exists.",
          String.format(
              "Required subscription not exists. Create subscription: %s for tenant: %s and restart service.",
              replayRoutingPropertySubscriptionName, dataPartitionId));
    } else {
      log.debug(
          "* * OqmReplayApiSubscriberManager on check for subscription {} existence: PRESENT.",
          replayRoutingPropertySubscriptionName);
    }
  }

  private static void checkTopic(OqmTopic topic, String replayRoutingTopicName, String dataPartitionId) {
    if (topic == null) {
      log.error(
          "* * OqmReplayApiSubscriberManager on check for topic {} existence: ABSENT.",
          replayRoutingTopicName);
      throw new AppException(
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Required topic not exists.",
          String.format(
              "Required topic not exists. Create topic: %s for tenant: %s and restart service.",
              replayRoutingTopicName, dataPartitionId));
    }
  }

  private static void checkParams(String replayRoutingPropertySubscriptionName, String replayRoutingTopicName) {
    if (replayRoutingPropertySubscriptionName == null || replayRoutingTopicName == null) {
      throw new AppException(
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Required topic or subscription not exists.",
          String.format(
              "Required topic or subscription not exists in properties. Create property topic: %s or subscription: %s.",
              replayRoutingTopicName, replayRoutingPropertySubscriptionName));
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
              "OqmReplayApiSubscriberManager {} {} {}",
              pubSubMessage,
              headerAttributes,
              oqmMessage.getId());

          boolean ackedNacked = false;
          try {
            dpsHeaders.setThreadContext(headerAttributes);
            ReplayMessageProcessing replayMessageProcessing =
                new ReplayMessageProcessing(replayMessageService, dpsHeaders);
            replayMessageProcessing.process(oqmMessage);
            log.debug(
                "OQM message handling for tenant {} topic {} subscription {}. ACK. Message = data: {}, attributes: {}.",
                dpsHeaders.getPartitionId(),
                replayRoutingProperty.get(TOPIC),
                replayRoutingProperty.get(TOPIC_SUBSCRIPTION),
                pubSubMessage,
                StringUtils.join(headerAttributes));
            oqmAckReplier.ack();
            ackedNacked = true;
          } catch (Exception e) {
            log.error(
                "OQM message handling error for tenant {} topic {} subscription {}. Message = data: {}, attributes: {}, error: {}.",
                dpsHeaders.getPartitionId(),
                replayRoutingProperty.get(TOPIC),
                replayRoutingProperty.get(TOPIC_SUBSCRIPTION),
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
        OqmSubscriber.builder().subscription(subscription).messageReceiver(receiver).build();
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
