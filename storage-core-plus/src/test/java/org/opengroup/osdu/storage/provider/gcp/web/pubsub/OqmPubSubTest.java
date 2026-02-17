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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.lang.IllegalStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.OqmDestination;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.opengroup.osdu.oqm.core.model.OqmTopic;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.provider.gcp.web.config.GcpAppServiceConfig;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;

@ExtendWith(MockitoExtension.class)
class OqmPubSubTest {

  private static final String TENANT_ID_1 = "test-partition";
  private static final String TEST_USER_1 = "user@example.com";
  private static final String ROUTING_TOPIC_1 = "test-topic";
  private static final String PUBLISHER_BATCH_SIZE_1 = "2";
  private static final String KIND_1 = "kind-1";
  private static final String KIND_2 = "kind-2";
  private static final String KIND_3 = "kind-3";

  @Mock
  private IMessageBus publisher;
  @Mock
  private OqmDriver driver;
  @Mock
  private GcpAppServiceConfig config;
  @Mock
  private TenantInfo tenantInfo;

  @BeforeEach
  void setUp() {
    publisher = new OqmPubSub(config, driver, tenantInfo);
  }

  @Test
  void shouldPublishBatchedJsonMessages_withList() {
    DpsHeaders headers = new DpsHeaders();
    headers.put(DpsHeaders.USER_EMAIL, TEST_USER_1);
    headers.put(DpsHeaders.DATA_PARTITION_ID, TENANT_ID_1);

    Map<String, String> routingInfo = new HashMap<>();
    routingInfo.put(OqmPubSub.ROUTING_KEY, ROUTING_TOPIC_1);
    routingInfo.put(OqmPubSub.PUBLISHER_BATCH_SIZE, PUBLISHER_BATCH_SIZE_1);

    List<String> messages = List.of("m1", "m2", "m3");

    publisher.publishMessage(headers, routingInfo, messages);

    verify(driver, times(2))
        .publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
  }

  @Test
  void shouldPublishBatchedJsonMessages_withArray() {
    DpsHeaders headers = new DpsHeaders();
    headers.put(DpsHeaders.USER_EMAIL, TEST_USER_1);
    headers.put(DpsHeaders.DATA_PARTITION_ID, TENANT_ID_1);

    Map<String, String> routingInfo = new HashMap<>();
    routingInfo.put(OqmPubSub.ROUTING_KEY, ROUTING_TOPIC_1);
    routingInfo.put(OqmPubSub.PUBLISHER_BATCH_SIZE, PUBLISHER_BATCH_SIZE_1);

    PubSubInfo[] messages = {
      new PubSubInfo("1", KIND_1, OperationType.create),
      new PubSubInfo("2", KIND_2, OperationType.delete),
      new PubSubInfo("3", KIND_3, OperationType.update)
    };

    publisher.publishMessage(headers, routingInfo, messages);

    verify(driver, times(2))
        .publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
  }

  @Test
  void shouldNotPublishIfRoutingKeyMissing() {
    DpsHeaders headers = new DpsHeaders();
    headers.put(DpsHeaders.USER_EMAIL, TEST_USER_1);
    headers.put(DpsHeaders.DATA_PARTITION_ID, TENANT_ID_1);

    Map<String, String> routingInfo = new HashMap<>();
    List<String> messages = List.of("m1");

    publisher.publishMessage(headers, routingInfo, messages);

    verify(driver, never())
        .publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
  }

  @Test
  void shouldThrowIllegalStateException_whenPublishMessageWithCollaborationContextCalledWithoutV2Topic() {
    DpsHeaders headers = new DpsHeaders();
    Optional<CollaborationContext> context = Optional.empty();

    assertThrows(
        IllegalStateException.class,
        () -> publisher.publishMessage(context, headers, new RecordChangedV2()));
  }
}
