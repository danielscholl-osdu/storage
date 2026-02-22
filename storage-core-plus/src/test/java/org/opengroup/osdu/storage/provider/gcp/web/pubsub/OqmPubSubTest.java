/*
 *  Copyright @ Microsoft Corporation
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class OqmPubSubTest {

  private static final String TENANT_ID = "test-partition";
  private static final String TENANT_NAME = "test-tenant";
  private static final String TEST_USER = "user@example.com";
  private static final String DEFAULT_TOPIC = "default-topic";
  private static final String V2_TOPIC = "v2-topic";
  private static final String ROUTING_TOPIC = "routing-topic";
  private static final String CORRELATION_ID = "corr-123";
  private static final String KIND_1 = "kind-1";
  private static final String KIND_2 = "kind-2";
  private static final String KIND_3 = "kind-3";

  @Mock
  private OqmDriver driver;
  @Mock
  private GcpAppServiceConfig config;
  @Mock
  private TenantInfo tenantInfo;

  @Captor
  private ArgumentCaptor<OqmMessage> messageCaptor;

  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    lenient().when(tenantInfo.getName()).thenReturn(TENANT_NAME);
    lenient().when(config.getPubsubSearchTopic()).thenReturn(DEFAULT_TOPIC);

    Logger logger = (Logger) LoggerFactory.getLogger(OqmPubSub.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    Logger logger = (Logger) LoggerFactory.getLogger(OqmPubSub.class);
    logger.detachAppender(logAppender);
  }

  @Test
  void shouldPublishBatchedJsonMessages_withList() {
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);

    DpsHeaders headers = createHeaders();
    Map<String, String> routingInfo = new HashMap<>();
    routingInfo.put(OqmPubSub.ROUTING_KEY, ROUTING_TOPIC);
    routingInfo.put(OqmPubSub.PUBLISHER_BATCH_SIZE, "2");
    List<String> messages = List.of("m1", "m2", "m3");

    publisher.publishMessage(headers, routingInfo, messages);

    verify(driver, times(2)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
  }

  @Test
  void shouldPublishBatchedJsonMessages_withArray() {
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);

    DpsHeaders headers = createHeaders();
    Map<String, String> routingInfo = new HashMap<>();
    routingInfo.put(OqmPubSub.ROUTING_KEY, ROUTING_TOPIC);
    routingInfo.put(OqmPubSub.PUBLISHER_BATCH_SIZE, "2");
    PubSubInfo[] messages = {
            new PubSubInfo("1", KIND_1, OperationType.create),
            new PubSubInfo("2", KIND_2, OperationType.delete),
            new PubSubInfo("3", KIND_3, OperationType.update)
    };

    publisher.publishMessage(headers, routingInfo, messages);

    verify(driver, times(2)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
  }

  @Test
  void shouldNotPublishIfRoutingKeyMissing() {
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);

    DpsHeaders headers = createHeaders();
    Map<String, String> routingInfo = new HashMap<>();
    List<String> messages = List.of("m1");

    publisher.publishMessage(headers, routingInfo, messages);

    verify(driver, never()).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
  }

  @Test
  void publishMessage_shouldPublishSingleBatchForSmallArray() {
    when(config.getPubsubSearchTopic()).thenReturn(DEFAULT_TOPIC);
    when(tenantInfo.getName()).thenReturn(TENANT_NAME);
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);
    publisher.postConstruct();

    DpsHeaders headers = createHeaders();
    PubSubInfo[] messages = {
            new PubSubInfo("1", KIND_1, OperationType.create),
            new PubSubInfo("2", KIND_2, OperationType.delete)
    };

    publisher.publishMessage(headers, messages);

    verify(driver, times(1)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
  }

  @Test
  void publishMessage_shouldPublishMultipleBatches() {
    when(config.getPubsubSearchTopic()).thenReturn(DEFAULT_TOPIC);
    when(tenantInfo.getName()).thenReturn(TENANT_NAME);
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);
    publisher.postConstruct();

    DpsHeaders headers = createHeaders();
    PubSubInfo[] messages = new PubSubInfo[51];
    for (int i = 0; i < 51; i++) {
      messages[i] = new PubSubInfo(String.valueOf(i), KIND_1, OperationType.create);
    }

    publisher.publishMessage(headers, messages);

    verify(driver, times(2)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
  }

  @Test
  void publishMessageV2_shouldThrowExceptionWhenNotConfigured() {
    when(config.getPubsubSearchTopic()).thenReturn(DEFAULT_TOPIC);
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);
    publisher.postConstruct();

    RecordChangedV2[] messages = {new RecordChangedV2()};
    Optional<CollaborationContext> emptyContext = Optional.empty();
    DpsHeaders headers = createHeaders();

    assertThrows(IllegalStateException.class, () ->
            publisher.publishMessage(emptyContext, headers, messages)
    );
  }

  @Test
  void publishMessageV2_shouldPublishWhenConfigured() {
    when(config.getPubsubSearchTopic()).thenReturn(DEFAULT_TOPIC);
    when(config.getPubsubSearchTopicV2()).thenReturn(V2_TOPIC);
    when(tenantInfo.getName()).thenReturn(TENANT_NAME);
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);
    publisher.postConstruct();

    DpsHeaders headers = createHeaders();
    RecordChangedV2[] messages = {new RecordChangedV2()};

    publisher.publishMessage(Optional.empty(), headers, messages);

    verify(driver, times(1)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
  }

  @Test
  void publishMessageV2_shouldIncludeCollaborationContext() {
    when(config.getPubsubSearchTopic()).thenReturn(DEFAULT_TOPIC);
    when(config.getPubsubSearchTopicV2()).thenReturn(V2_TOPIC);
    when(tenantInfo.getName()).thenReturn(TENANT_NAME);
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);
    publisher.postConstruct();

    DpsHeaders headers = createHeaders();
    CollaborationContext context = new CollaborationContext();
    context.setId(UUID.randomUUID());
    context.setApplication("test-app");
    RecordChangedV2[] messages = {new RecordChangedV2()};

    publisher.publishMessage(Optional.of(context), headers, messages);

    verify(driver).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
    Map<String, String> attributes = messageCaptor.getValue().getAttributes();
    assertNotNull(attributes.get(DpsHeaders.COLLABORATION));
  }

  @Test
  void getBatchSize_shouldReturnDefaultWhenMissing() {
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);
    Map<String, String> routingInfo = new HashMap<>();

    Integer batchSize = publisher.getBatchSize(routingInfo);

    assertEquals(50, batchSize);
  }

  @Test
  void getBatchSize_shouldReturnCustomValue() {
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);
    Map<String, String> routingInfo = new HashMap<>();
    routingInfo.put(OqmPubSub.PUBLISHER_BATCH_SIZE, "25");

    Integer batchSize = publisher.getBatchSize(routingInfo);

    assertEquals(25, batchSize);
  }

  @Test
  void getBatchSize_shouldReturnDefaultForNegative() {
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);
    Map<String, String> routingInfo = new HashMap<>();
    routingInfo.put(OqmPubSub.PUBLISHER_BATCH_SIZE, "-1");

    Integer batchSize = publisher.getBatchSize(routingInfo);

    assertEquals(50, batchSize);
  }

  @Test
  void buildAttributes_shouldIncludeAllHeaders() {
    when(tenantInfo.getName()).thenReturn(TENANT_NAME);
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);
    DpsHeaders headers = createHeaders();

    Map<String, String> attributes = publisher.buildAttributes(headers, tenantInfo);

    assertEquals(TEST_USER, attributes.get(DpsHeaders.USER_EMAIL));
    assertEquals(TENANT_NAME, attributes.get(DpsHeaders.ACCOUNT_ID));
    assertEquals(TENANT_ID, attributes.get(DpsHeaders.DATA_PARTITION_ID));
    assertNotNull(attributes.get(DpsHeaders.CORRELATION_ID));
  }

  @Test
  void getOqmDestination_shouldCreateDestination() {
    OqmPubSub publisher = new OqmPubSub(config, driver, tenantInfo);
    DpsHeaders headers = createHeaders();

    OqmDestination destination = publisher.getOqmDestination(headers);

    assertNotNull(destination);
    assertEquals(TENANT_ID, destination.getPartitionId());
  }

  private DpsHeaders createHeaders() {
    DpsHeaders headers = new DpsHeaders();
    headers.put(DpsHeaders.USER_EMAIL, TEST_USER);
    headers.put(DpsHeaders.DATA_PARTITION_ID, TENANT_ID);
    headers.put(DpsHeaders.CORRELATION_ID, CORRELATION_ID);
    return headers;
  }
}
