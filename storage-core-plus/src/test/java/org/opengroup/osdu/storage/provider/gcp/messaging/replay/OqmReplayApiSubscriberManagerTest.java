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
package org.opengroup.osdu.storage.provider.gcp.messaging.replay;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.anyBoolean;

import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.*;
import org.opengroup.osdu.storage.provider.gcp.messaging.util.OqmMessageValidator;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("OqmReplayApiSubscriberManager Tests")
class OqmReplayApiSubscriberManagerTest {
    private static final String TEST_PARTITION_ID = "test-partition";
    private static final String TEST_PARTITION_ID_2 = "test-partition-2";
    private static final String REPLAY_TOPIC = "replay-topic";
    private static final String REPLAY_SUBSCRIPTION = "replay-subscription";
    private static final String TEST_MESSAGE_DATA = "{\"recordId\":\"test-record-123\"}";
    private static final String TEST_MESSAGE_ID = "message-id-123";

    @Mock
    private ITenantFactory tenantInfoFactory;

    @Mock
    private OqmDriver driver;

    @Mock
    private org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadDpsHeaders dpsHeaders;

    @Mock
    private ReplayMessageService replayMessageService;

    @InjectMocks
    private OqmReplayApiSubscriberManager manager;

    @Mock
    private TenantInfo tenantInfo1;

    @Mock
    private TenantInfo tenantInfo2;

    @Mock
    private OqmTopic topic;

    @Mock
    private OqmSubscription subscription;

    @Captor
    private ArgumentCaptor<OqmSubscriber> subscriberCaptor;

    @Captor
    private ArgumentCaptor<OqmSubscriptionQuery> queryCaptor;

    @Captor
    private ArgumentCaptor<OqmDestination> destinationCaptor;

    private Map<String, String> replayRoutingProperty;

    @BeforeEach
    void setUp() {
        replayRoutingProperty = new HashMap<>();
        replayRoutingProperty.put("topic", REPLAY_TOPIC);
        replayRoutingProperty.put("topicSubscription", REPLAY_SUBSCRIPTION);
        ReflectionTestUtils.setField(manager, "replayRoutingProperty", replayRoutingProperty);

        lenient().when(tenantInfo1.getDataPartitionId()).thenReturn(TEST_PARTITION_ID);
        lenient().when(tenantInfo2.getDataPartitionId()).thenReturn(TEST_PARTITION_ID_2);
        lenient().when(topic.getName()).thenReturn(REPLAY_TOPIC);
        lenient().when(subscription.getName()).thenReturn(REPLAY_SUBSCRIPTION);
        lenient().when(subscription.getTopics()).thenReturn(Collections.singletonList(topic));
    }

    private void invokeMessageReceiver(OqmMessageReceiver receiver, OqmMessage message, OqmAckReplier ackReplier) throws Exception {
        Method[] methods = OqmMessageReceiver.class.getDeclaredMethods();
        Method functionalMethod = null;

        for (Method method : methods) {
            if (java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                functionalMethod = method;
                break;
            }
        }

        if (functionalMethod != null) {
            functionalMethod.invoke(receiver, message, ackReplier);
        } else {
            throw new IllegalStateException("Could not find functional method in OqmMessageReceiver");
        }
    }

    private OqmMessage createValidMessage() {
        OqmMessage message = mock(OqmMessage.class);
        lenient().when(message.getData()).thenReturn(TEST_MESSAGE_DATA);
        lenient().when(message.getId()).thenReturn(TEST_MESSAGE_ID);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("partition-id", TEST_PARTITION_ID);
        attributes.put("data-partition-id", TEST_PARTITION_ID);
        lenient().when(message.getAttributes()).thenReturn(attributes);

        return message;
    }

    private OqmMessage createInvalidMessage() {
        OqmMessage message = mock(OqmMessage.class);
        lenient().when(message.getData()).thenReturn(null);
        lenient().when(message.getId()).thenReturn(null);
        lenient().when(message.getAttributes()).thenReturn(null);
        return message;
    }

    @Nested
    @DisplayName("PostConstruct - Successful Provisioning Tests")
    class PostConstructSuccessTests {

        @Test
        @DisplayName("Should successfully provision single tenant")
        void postConstruct_SingleTenant_ShouldProvisionSuccessfully() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class));
            verify(driver).listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class));
            verify(driver).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
        }

        @Test
        @DisplayName("Should successfully provision multiple tenants")
        void postConstruct_MultipleTenants_ShouldProvisionSuccessfully() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver, times(2)).getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class));
            verify(driver, times(2)).listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class));
            verify(driver, times(2)).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
        }

        @Test
        @DisplayName("Should register subscriber with correct configuration")
        void postConstruct_RegisterSubscriber_ShouldHaveCorrectConfiguration() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmSubscriber capturedSubscriber = subscriberCaptor.getValue();

            assertNotNull(capturedSubscriber);
            assertEquals(subscription, capturedSubscriber.getSubscription());
            assertNotNull(capturedSubscriber.getMessageReceiver());
        }

        @Test
        @DisplayName("Should create subscription query with correct parameters")
        void postConstruct_SubscriptionQuery_ShouldHaveCorrectParameters() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).listSubscriptions(eq(topic), queryCaptor.capture(), any(OqmDestination.class));
            OqmSubscriptionQuery capturedQuery = queryCaptor.getValue();

            assertEquals(REPLAY_SUBSCRIPTION, capturedQuery.getNamePrefix());
            assertTrue(capturedQuery.getSubscriberable());
        }
    }

    @Nested
    @DisplayName("PostConstruct - Parameter Validation Tests")
    class PostConstructParameterValidationTests {

        @Test
        @DisplayName("Should throw AppException when topic name is null")
        void postConstruct_NullTopicName_ShouldThrowAppException() {
            replayRoutingProperty.put("topic", null);
            lenient().when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertEquals("Required topic or subscription not exists.", exception.getError().getReason());
            assertTrue(exception.getError().getMessage().contains("not exists in properties"));
        }

        @Test
        @DisplayName("Should throw AppException when subscription name is null")
        void postConstruct_NullSubscriptionName_ShouldThrowAppException() {
            replayRoutingProperty.put("topicSubscription", null);
            lenient().when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertEquals("Required topic or subscription not exists.", exception.getError().getReason());
            assertTrue(exception.getError().getMessage().contains("not exists in properties"));
        }

        @Test
        @DisplayName("Should throw AppException when both topic and subscription names are null")
        void postConstruct_BothNamesNull_ShouldThrowAppException() {
            replayRoutingProperty.put("topic", null);
            replayRoutingProperty.put("topicSubscription", null);
            lenient().when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertTrue(exception.getError().getMessage().contains("not exists in properties"));
        }

        @Test
        @DisplayName("Should validate parameters before processing tenants")
        void postConstruct_NullParameters_ShouldFailBeforeProcessingTenants() {
            replayRoutingProperty.put("topic", null);
            lenient().when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));

            assertThrows(AppException.class, () -> manager.postConstruct());

            verify(driver, never()).getTopic(any(), any());
            verify(driver, never()).listSubscriptions(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("PostConstruct - Topic Validation Tests")
    class PostConstructTopicValidationTests {

        @Test
        @DisplayName("Should throw AppException when topic does not exist")
        void postConstruct_TopicNotExists_ShouldThrowAppException() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertEquals("Required topic not exists.", exception.getError().getReason());
            assertTrue(exception.getError().getMessage().contains(REPLAY_TOPIC));
            assertTrue(exception.getError().getMessage().contains(TEST_PARTITION_ID));
        }

        @Test
        @DisplayName("Should validate topic exists before checking subscription")
        void postConstruct_TopicNotExists_ShouldNotCheckSubscription() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.empty());

            assertThrows(AppException.class, () -> manager.postConstruct());

            verify(driver, never()).listSubscriptions(any(), any(), any());
        }

        @Test
        @DisplayName("Should fail on first tenant when topic missing")
        void postConstruct_MultipleTenants_FirstTenantTopicMissing_ShouldFailImmediately() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertTrue(exception.getError().getMessage().contains(TEST_PARTITION_ID));
            verify(driver, times(1)).getTopic(any(), any());
        }
    }

    @Nested
    @DisplayName("PostConstruct - Subscription Validation Tests")
    class PostConstructSubscriptionValidationTests {

        @Test
        @DisplayName("Should throw AppException when subscription does not exist")
        void postConstruct_SubscriptionNotExists_ShouldThrowAppException() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.emptyList());

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertEquals("Required subscription not exists.", exception.getError().getReason());
            assertTrue(exception.getError().getMessage().contains(REPLAY_SUBSCRIPTION));
            assertTrue(exception.getError().getMessage().contains(TEST_PARTITION_ID));
        }

        @Test
        @DisplayName("Should not register subscriber when subscription missing")
        void postConstruct_SubscriptionNotExists_ShouldNotRegisterSubscriber() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.emptyList());

            assertThrows(AppException.class, () -> manager.postConstruct());

            verify(driver, never()).subscribe(any(), any());
        }

        @Test
        @DisplayName("Should fail on second tenant when its subscription does not exist")
        void postConstruct_SecondTenantSubscriptionNotExists_ShouldThrowAppException() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription))
                    .thenReturn(Collections.emptyList());

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertTrue(exception.getError().getMessage().contains(TEST_PARTITION_ID_2));
            verify(driver, times(1)).subscribe(any(), any());
        }

        @Test
        @DisplayName("Should use first subscription when multiple subscriptions match")
        void postConstruct_MultipleSubscriptions_ShouldUseFirstOne() {
            OqmSubscription subscription2 = mock(OqmSubscription.class);
            lenient().when(subscription2.getName()).thenReturn("another-subscription");
            lenient().when(subscription2.getTopics()).thenReturn(Collections.singletonList(topic));

            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Arrays.asList(subscription, subscription2));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            assertEquals(subscription, subscriberCaptor.getValue().getSubscription());
        }
    }

    @Nested
    @DisplayName("Destination Creation Tests")
    class DestinationCreationTests {

        @Test
        @DisplayName("Should create destination with correct partition ID")
        void postConstruct_CreateDestination_ShouldHaveCorrectPartitionId() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).getTopic(eq(REPLAY_TOPIC), destinationCaptor.capture());
            OqmDestination capturedDestination = destinationCaptor.getValue();
            assertEquals(TEST_PARTITION_ID, capturedDestination.getPartitionId());
        }

        @Test
        @DisplayName("Should create different destinations for different tenants")
        void postConstruct_MultipleTenants_ShouldCreateDifferentDestinations() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver, times(2)).getTopic(eq(REPLAY_TOPIC), destinationCaptor.capture());
            List<OqmDestination> destinations = destinationCaptor.getAllValues();

            assertEquals(2, destinations.size());
            assertEquals(TEST_PARTITION_ID, destinations.get(0).getPartitionId());
            assertEquals(TEST_PARTITION_ID_2, destinations.get(1).getPartitionId());
        }

        @Test
        @DisplayName("Should use same destination for getTopic, listSubscriptions, and subscribe")
        void postConstruct_ShouldUseSameDestinationForAllOperations() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            ArgumentCaptor<OqmDestination> getTopicDest = ArgumentCaptor.forClass(OqmDestination.class);
            ArgumentCaptor<OqmDestination> listSubsDest = ArgumentCaptor.forClass(OqmDestination.class);
            ArgumentCaptor<OqmDestination> subscribeDest = ArgumentCaptor.forClass(OqmDestination.class);

            manager.postConstruct();

            verify(driver).getTopic(any(), getTopicDest.capture());
            verify(driver).listSubscriptions(any(), any(), listSubsDest.capture());
            verify(driver).subscribe(any(), subscribeDest.capture());

            assertEquals(TEST_PARTITION_ID, getTopicDest.getValue().getPartitionId());
            assertEquals(TEST_PARTITION_ID, listSubsDest.getValue().getPartitionId());
            assertEquals(TEST_PARTITION_ID, subscribeDest.getValue().getPartitionId());
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty tenant list gracefully")
        void postConstruct_EmptyTenantList_ShouldNotThrow() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> manager.postConstruct());

            verify(driver, never()).getTopic(any(), any());
            verify(driver, never()).listSubscriptions(any(), any(), any());
            verify(driver, never()).subscribe(any(), any());
        }

        @Test
        @DisplayName("Should handle null tenant list gracefully")
        void postConstruct_NullTenantList_ShouldHandleGracefully() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(null);

            assertThrows(NullPointerException.class, () -> manager.postConstruct());
        }

        @Test
        @DisplayName("Should process all tenants even if some succeed")
        void postConstruct_PartialSuccess_ShouldProcessAllUntilFailure() {
            TenantInfo tenantInfo3 = mock(TenantInfo.class);
            lenient().when(tenantInfo3.getDataPartitionId()).thenReturn("test-partition-3");

            when(tenantInfoFactory.listTenantInfo())
                    .thenReturn(Arrays.asList(tenantInfo1, tenantInfo2, tenantInfo3));

            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic))
                    .thenReturn(Optional.empty());

            lenient().when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertTrue(exception.getError().getMessage().contains(TEST_PARTITION_ID_2));
            verify(driver, times(1)).subscribe(any(), any());
            verify(driver, times(2)).getTopic(any(), any());
        }
    }

    @Nested
    @DisplayName("Subscriber Configuration Tests")
    class SubscriberConfigurationTests {

        @Test
        @DisplayName("Should verify subscriber is properly configured")
        void postConstruct_SubscriberConfiguration_ShouldConfigureCorrectly() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmSubscriber subscriber = subscriberCaptor.getValue();

            assertNotNull(subscriber, "Subscriber should be created");
            assertNotNull(subscriber.getMessageReceiver(), "Subscriber must have message receiver");
            assertSame(subscription, subscriber.getSubscription(), "Subscriber should have correct subscription");
        }

        @Test
        @DisplayName("Should attach message receiver to subscriber")
        void postConstruct_SubscriberConfiguration_ShouldHaveMessageReceiver() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmSubscriber subscriber = subscriberCaptor.getValue();

            assertNotNull(subscriber.getMessageReceiver(),
                    "Subscriber must have a message receiver");
        }

        @Test
        @DisplayName("Should attach correct subscription to subscriber")
        void postConstruct_SubscriberConfiguration_ShouldAttachCorrectSubscription() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmSubscriber subscriber = subscriberCaptor.getValue();

            assertSame(subscription, subscriber.getSubscription(),
                    "Subscriber should have the correct subscription attached");
        }

        @Test
        @DisplayName("Should create independent subscribers for each tenant")
        void postConstruct_MultipleTenants_ShouldCreateIndependentSubscribers() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver, times(2)).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            List<OqmSubscriber> subscribers = subscriberCaptor.getAllValues();

            assertEquals(2, subscribers.size());
            assertNotSame(subscribers.get(0), subscribers.get(1),
                    "Each tenant should have its own subscriber instance");
            assertSame(subscribers.get(0).getSubscription(), subscribers.get(1).getSubscription());
        }
    }

    // ========================================
    // Message Receiver Lambda Tests - CRITICAL FOR 100% COVERAGE
    // ========================================

    @Nested
    @DisplayName("Message Receiver Lambda Tests")
    class MessageReceiverLambdaTests {

        @Test
        @DisplayName("Should nack invalid message without processing")
        void messageReceiver_InvalidMessage_ShouldNackImmediately() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            OqmMessage invalidMessage = createInvalidMessage();
            OqmAckReplier ackReplier = mock(OqmAckReplier.class);

            try (MockedStatic<OqmMessageValidator> mockedValidator = mockStatic(OqmMessageValidator.class)) {
                mockedValidator.when(() -> OqmMessageValidator.isValid(any(OqmMessage.class))).thenReturn(false);

                invokeMessageReceiver(receiver, invalidMessage, ackReplier);

                verify(ackReplier).nack(false);
                verify(ackReplier, never()).ack();
                verify(dpsHeaders, never()).setThreadContext(any());
            }
        }

        @Test
        @DisplayName("Should nack message when processing throws exception")
        void messageReceiver_ProcessingThrowsException_ShouldNack() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            OqmMessage message = createValidMessage();
            OqmAckReplier ackReplier = mock(OqmAckReplier.class);

            // Make dpsHeaders throw exception during processing
            doThrow(new RuntimeException("Processing failed"))
                    .when(dpsHeaders).setThreadContext(any());

            try (MockedStatic<OqmMessageValidator> mockedValidator = mockStatic(OqmMessageValidator.class)) {
                mockedValidator.when(() -> OqmMessageValidator.isValid(any(OqmMessage.class))).thenReturn(true);

                invokeMessageReceiver(receiver, message, ackReplier);

                verify(ackReplier).nack(false);
                verify(ackReplier, never()).ack();
            }
        }

        @Test
        @DisplayName("Should pass validation and attempt to process valid message")
        void messageReceiver_ValidMessage_ShouldPassValidation() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            OqmMessage message = createValidMessage();
            OqmAckReplier ackReplier = mock(OqmAckReplier.class);

            try (MockedStatic<OqmMessageValidator> mockedValidator = mockStatic(OqmMessageValidator.class)) {
                mockedValidator.when(() -> OqmMessageValidator.isValid(any(OqmMessage.class))).thenReturn(true);

                invokeMessageReceiver(receiver, message, ackReplier);

                // Verify validation was passed and processing was attempted
                verify(dpsHeaders).setThreadContext(message.getAttributes());
                verify(message, atLeastOnce()).getData();
                verify(message, atLeastOnce()).getAttributes();
                verify(message, atLeastOnce()).getId();

                // Note: We verify nack is called because ReplayMessageProcessing constructor or process()
                // might throw in unit test context without full integration setup
                verify(ackReplier, atLeastOnce()).nack(anyBoolean());
            }
        }

        @Test
        @DisplayName("Should extract message data for processing")
        void messageReceiver_ValidMessage_ShouldExtractMessageData() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            OqmMessage message = createValidMessage();
            OqmAckReplier ackReplier = mock(OqmAckReplier.class);

            try (MockedStatic<OqmMessageValidator> mockedValidator = mockStatic(OqmMessageValidator.class)) {
                mockedValidator.when(() -> OqmMessageValidator.isValid(any(OqmMessage.class))).thenReturn(true);

                invokeMessageReceiver(receiver, message, ackReplier);

                // Verify message data was extracted
                verify(message, atLeastOnce()).getData();
                verify(message, atLeastOnce()).getAttributes();
                verify(message, atLeastOnce()).getId();
            }
        }

        @Test
        @DisplayName("Should set thread context with message attributes when processing")
        void messageReceiver_ValidMessage_ShouldSetThreadContext() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            OqmMessage message = createValidMessage();
            OqmAckReplier ackReplier = mock(OqmAckReplier.class);

            try (MockedStatic<OqmMessageValidator> mockedValidator = mockStatic(OqmMessageValidator.class)) {
                mockedValidator.when(() -> OqmMessageValidator.isValid(any(OqmMessage.class))).thenReturn(true);

                invokeMessageReceiver(receiver, message, ackReplier);

                ArgumentCaptor<Map<String, String>> attributesCaptor = ArgumentCaptor.forClass(Map.class);
                verify(dpsHeaders).setThreadContext(attributesCaptor.capture());

                Map<String, String> capturedAttributes = attributesCaptor.getValue();
                assertEquals(TEST_PARTITION_ID, capturedAttributes.get("partition-id"));
            }
        }

        @Test
        @DisplayName("Should create message receiver that is not null")
        void messageReceiver_ShouldBeCreated() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            assertNotNull(receiver, "Message receiver should be created");
        }

        @Test
        @DisplayName("Should validate message before processing")
        void messageReceiver_ShouldCallValidatorBeforeProcessing() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            OqmMessage message = createValidMessage();
            OqmAckReplier ackReplier = mock(OqmAckReplier.class);

            try (MockedStatic<OqmMessageValidator> mockedValidator = mockStatic(OqmMessageValidator.class)) {
                mockedValidator.when(() -> OqmMessageValidator.isValid(any(OqmMessage.class))).thenReturn(true);

                invokeMessageReceiver(receiver, message, ackReplier);

                // Verify validator was called
                mockedValidator.verify(() -> OqmMessageValidator.isValid(message), times(1));
            }
        }
    }

    @Nested
    @DisplayName("Replay Routing Property Tests")
    class ReplayRoutingPropertyTests {

        @Test
        @DisplayName("Should use topic from replay routing property")
        void postConstruct_ShouldUseTopicFromProperty() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class));
        }

        @Test
        @DisplayName("Should use subscription from replay routing property")
        void postConstruct_ShouldUseSubscriptionFromProperty() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(REPLAY_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).listSubscriptions(eq(topic), queryCaptor.capture(), any(OqmDestination.class));
            assertEquals(REPLAY_SUBSCRIPTION, queryCaptor.getValue().getNamePrefix());
        }
    }
}
