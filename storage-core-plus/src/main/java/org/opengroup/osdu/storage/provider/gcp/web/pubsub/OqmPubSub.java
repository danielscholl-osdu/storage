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

package org.opengroup.osdu.storage.provider.gcp.web.pubsub;

import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.OqmDestination;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.opengroup.osdu.oqm.core.model.OqmTopic;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.provider.gcp.web.config.GcpAppServiceConfig;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class OqmPubSub implements IMessageBus {

  protected static final String PUBLISHER_BATCH_SIZE = "publisherBatchSize";
  protected static final int BATCH_SIZE = 50;
  protected static final String ROUTING_KEY = "topic";

  private final GcpAppServiceConfig config;
  private final OqmDriver driver;
  private final TenantInfo tenant;
  private final Gson gson = new Gson();

  private OqmTopic oqmTopic = null;

  @PostConstruct
  void postConstruct() {
    oqmTopic = getOqmTopic(config.getPubsubSearchTopic());
  }

  @Override
  public void publishMessage(DpsHeaders headers, PubSubInfo... messages) {
    OqmDestination oqmDestination = getOqmDestination(headers);

    for (int i = 0; i < messages.length; i += BATCH_SIZE) {
      PubSubInfo[] batch =
          Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));
      String json = gson.toJson(batch);
      OqmMessage oqmMessage = OqmMessage.builder().data(json).attributes(buildAttributes(headers, tenant)).build();

      driver.publish(oqmMessage, oqmTopic, oqmDestination);
    }
  }

  @Override
  public void publishMessage(Optional<CollaborationContext> collaborationContext, DpsHeaders headers, RecordChangedV2... messages) {
    throw new NotImplementedException();
  }

  @Override
  public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, List<?> messageList) {
    OqmDestination oqmDestination = getOqmDestination(headers);

    int batchSize = getBatchSize(routingInfo);

    if (routingInfo.get(ROUTING_KEY) != null) {
      OqmTopic routingTopic = getOqmTopic(routingInfo.get(ROUTING_KEY));
      for (int i = 0; i < messageList.size(); i += batchSize) {
        List<?> batch = messageList.subList(i, Math.min(i + batchSize, messageList.size()));
        String json = gson.toJson(batch);
        OqmMessage oqmMessage = OqmMessage.builder().data(json).attributes(buildAttributes(headers, tenant)).build();

        driver.publish(oqmMessage, routingTopic, oqmDestination);
      }
    } else {
      log.warn("Replay API is not configured. Please, check {}.", ROUTING_KEY);
    }
  }

  @Override
  public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, PubSubInfo... messages) {

    OqmDestination oqmDestination = getOqmDestination(headers);
    int batchSize = getBatchSize(routingInfo);

    if (routingInfo.get(ROUTING_KEY) != null) {
      OqmTopic routingTopic = getOqmTopic(routingInfo.get(ROUTING_KEY));
      for (int i = 0; i < messages.length; i += batchSize) {
        PubSubInfo[] batch =
            Arrays.copyOfRange(messages, i, Math.min(messages.length, i + batchSize));
        String json = gson.toJson(batch);
        OqmMessage oqmMessage = OqmMessage.builder().data(json).attributes(buildAttributes(headers, tenant)).build();

        driver.publish(oqmMessage, routingTopic, oqmDestination);
      }
    } else {
      log.warn("Replay API is not configured. Please, check {}.", ROUTING_KEY);
    }
  }

  private static OqmTopic getOqmTopic(String name) {
    return OqmTopic.builder().name(name).build();
  }

  protected OqmDestination getOqmDestination(DpsHeaders headers) {
    return OqmDestination.builder().partitionId(headers.getPartitionId()).build();
  }

  protected Integer getBatchSize(Map<String, String> routingInfo) {
    return Optional.ofNullable(routingInfo.get(PUBLISHER_BATCH_SIZE))
        .filter(s -> !s.isEmpty())
        .map(Integer::parseInt)
        .filter(size -> size >= 0)
        .orElse(BATCH_SIZE);
  }

  protected Map<String, String> buildAttributes(DpsHeaders headers, TenantInfo tenant) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(DpsHeaders.USER_EMAIL, headers.getUserEmail());
    attributes.put(DpsHeaders.ACCOUNT_ID, tenant.getName());
    attributes.put(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
    headers.addCorrelationIdIfMissing();
    attributes.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
    return attributes;
  }
}
