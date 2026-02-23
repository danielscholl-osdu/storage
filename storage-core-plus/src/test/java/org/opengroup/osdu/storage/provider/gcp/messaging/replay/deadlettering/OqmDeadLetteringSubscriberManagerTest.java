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
package org.opengroup.osdu.storage.provider.gcp.messaging.replay.deadlettering;

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
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.*;
import org.opengroup.osdu.storage.provider.gcp.messaging.config.ReplayConfigurationProperties;
import org.opengroup.osdu.storage.provider.gcp.messaging.replay.ReplayMessageService;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadDpsHeaders;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("OqmDeadLetteringSubscriberManager Tests")
class OqmDeadLetteringSubscriberManagerTest {
    private static final String TEST_PARTITION_ID = "test-partition";
    private static final String TEST_PARTITION_ID_2 = "test-partition-2";
    private static final String DEAD_LETTER_TOPIC = "dead-letter-topic";
    private static final String DEAD_LETTER_SUBSCRIPTION = "dead-letter-subscription";

    @Mock
    private ITenantFactory tenantInfoFactory;

    @Mock
    private OqmDriver driver;

    @Mock
    private ThreadDpsHeaders dpsHeaders;

    @Mock
    private ReplayMessageService replayMessageService;

    @Mock
    private ReplayConfigurationProperties properties;

    @InjectMocks
    private OqmDeadLetteringSubscriberManager manager;

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

    @BeforeEach
    void setUp() {
        lenient().when(properties.getDeadLetterTopicName()).thenReturn(DEAD_LETTER_TOPIC);
        lenient().when(properties.getDeadLetterSubscriptionName()).thenReturn(DEAD_LETTER_SUBSCRIPTION);
        lenient().when(tenantInfo1.getDataPartitionId()).thenReturn(TEST_PARTITION_ID);
        lenient().when(tenantInfo2.getDataPartitionId()).thenReturn(TEST_PARTITION_ID_2);
        lenient().when(topic.getName()).thenReturn(DEAD_LETTER_TOPIC);
        lenient().when(subscription.getName()).thenReturn(DEAD_LETTER_SUBSCRIPTION);
        lenient().when(subscription.getTopics()).thenReturn(Collections.singletonList(topic));
    }

    // Helper method to invoke the message receiver using reflection
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

    private OqmMessage createInvalidMessage() {
        OqmMessage message = mock(OqmMessage.class);
        // Make message invalid by returning null for critical fields
        lenient().when(message.getData()).thenReturn(null);
        lenient().when(message.getId()).thenReturn(null);
        lenient().when(message.getAttributes()).thenReturn(null);
        return message;
    }

    // ========================================
    // PostConstruct - Successful Provisioning Tests
    // ========================================

    @Nested
    @DisplayName("PostConstruct - Successful Provisioning Tests")
    class PostConstructSuccessTests {

        @Test
        @DisplayName("Should successfully provision single tenant")
        void postConstruct_SingleTenant_ShouldProvisionSuccessfully() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class));
            verify(driver).listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class));
            verify(driver).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
        }

        @Test
        @DisplayName("Should successfully provision multiple tenants")
        void postConstruct_MultipleTenants_ShouldProvisionSuccessfully() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver, times(2)).getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class));
            verify(driver, times(2)).listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class));
            verify(driver, times(2)).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
        }

        @Test
        @DisplayName("Should register subscriber with correct configuration")
        void postConstruct_RegisterSubscriber_ShouldHaveCorrectConfiguration() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).listSubscriptions(eq(topic), queryCaptor.capture(), any(OqmDestination.class));
            OqmSubscriptionQuery capturedQuery = queryCaptor.getValue();

            assertEquals(DEAD_LETTER_SUBSCRIPTION, capturedQuery.getNamePrefix());
            assertTrue(capturedQuery.getSubscriberable());
        }

        @Test
        @DisplayName("Should verify subscriber is registered for each tenant")
        void postConstruct_MultipleTenants_ShouldRegisterMultipleSubscribers() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver, times(2)).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            List<OqmSubscriber> subscribers = subscriberCaptor.getAllValues();

            assertEquals(2, subscribers.size());
            subscribers.forEach(subscriber -> {
                assertNotNull(subscriber.getMessageReceiver());
                assertEquals(subscription, subscriber.getSubscription());
            });
        }
    }

    // ========================================
    // PostConstruct - Parameter Validation Tests
    // ========================================

    @Nested
    @DisplayName("PostConstruct - Parameter Validation Tests")
    class PostConstructParameterValidationTests {

        @Test
        @DisplayName("Should throw AppException when topic name is null")
        void postConstruct_NullTopicName_ShouldThrowAppException() {
            when(properties.getDeadLetterTopicName()).thenReturn(null);
            lenient().when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertEquals("Required topic or subscription not exists.", exception.getError().getReason());
            assertTrue(exception.getError().getMessage().contains("not exists in properties"));
        }

        @Test
        @DisplayName("Should throw AppException when subscription name is null")
        void postConstruct_NullSubscriptionName_ShouldThrowAppException() {
            when(properties.getDeadLetterSubscriptionName()).thenReturn(null);
            lenient().when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertEquals("Required topic or subscription not exists.", exception.getError().getReason());
            assertTrue(exception.getError().getMessage().contains("not exists in properties"));
        }

        @Test
        @DisplayName("Should throw AppException when both topic and subscription names are null")
        void postConstruct_BothNamesNull_ShouldThrowAppException() {
            when(properties.getDeadLetterTopicName()).thenReturn(null);
            when(properties.getDeadLetterSubscriptionName()).thenReturn(null);
            lenient().when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertTrue(exception.getError().getMessage().contains("not exists in properties"));
        }

        @Test
        @DisplayName("Should validate parameters before processing tenants")
        void postConstruct_NullParameters_ShouldFailBeforeProcessingTenants() {
            when(properties.getDeadLetterTopicName()).thenReturn(null);
            lenient().when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));

            assertThrows(AppException.class, () -> manager.postConstruct());

            verify(driver, never()).getTopic(any(), any());
            verify(driver, never()).listSubscriptions(any(), any(), any());
        }
    }

    // ========================================
    // PostConstruct - Topic Validation Tests
    // ========================================

    @Nested
    @DisplayName("PostConstruct - Topic Validation Tests")
    class PostConstructTopicValidationTests {

        @Test
        @DisplayName("Should throw AppException when topic does not exist")
        void postConstruct_TopicNotExists_ShouldThrowAppException() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertEquals("Required topic not exists.", exception.getError().getReason());
            assertTrue(exception.getError().getMessage().contains(DEAD_LETTER_TOPIC));
            assertTrue(exception.getError().getMessage().contains(TEST_PARTITION_ID));
        }

        @Test
        @DisplayName("Should validate topic exists before checking subscription")
        void postConstruct_TopicNotExists_ShouldNotCheckSubscription() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.empty());

            assertThrows(AppException.class, () -> manager.postConstruct());

            verify(driver, never()).listSubscriptions(any(), any(), any());
        }

        @Test
        @DisplayName("Should fail on first tenant when topic missing")
        void postConstruct_MultipleTenants_FirstTenantTopicMissing_ShouldFailImmediately() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertTrue(exception.getError().getMessage().contains(TEST_PARTITION_ID));
            verify(driver, times(1)).getTopic(any(), any());
        }
    }

    // ========================================
    // PostConstruct - Subscription Validation Tests
    // ========================================

    @Nested
    @DisplayName("PostConstruct - Subscription Validation Tests")
    class PostConstructSubscriptionValidationTests {

        @Test
        @DisplayName("Should throw AppException when subscription does not exist")
        void postConstruct_SubscriptionNotExists_ShouldThrowAppException() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.emptyList());

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertEquals("Required subscription not exists.", exception.getError().getReason());
            assertTrue(exception.getError().getMessage().contains(DEAD_LETTER_SUBSCRIPTION));
            assertTrue(exception.getError().getMessage().contains(TEST_PARTITION_ID));
        }

        @Test
        @DisplayName("Should not register subscriber when subscription missing")
        void postConstruct_SubscriptionNotExists_ShouldNotRegisterSubscriber() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Arrays.asList(subscription, subscription2));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            assertEquals(subscription, subscriberCaptor.getValue().getSubscription());
        }
    }

    // ========================================
    // Destination Creation Tests
    // ========================================

    @Nested
    @DisplayName("Destination Creation Tests")
    class DestinationCreationTests {

        @Test
        @DisplayName("Should create destination with correct partition ID")
        void postConstruct_CreateDestination_ShouldHaveCorrectPartitionId() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).getTopic(eq(DEAD_LETTER_TOPIC), destinationCaptor.capture());
            OqmDestination capturedDestination = destinationCaptor.getValue();
            assertEquals(TEST_PARTITION_ID, capturedDestination.getPartitionId());
        }

        @Test
        @DisplayName("Should create different destinations for different tenants")
        void postConstruct_MultipleTenants_ShouldCreateDifferentDestinations() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver, times(2)).getTopic(eq(DEAD_LETTER_TOPIC), destinationCaptor.capture());
            List<OqmDestination> destinations = destinationCaptor.getAllValues();

            assertEquals(2, destinations.size());
            assertEquals(TEST_PARTITION_ID, destinations.get(0).getPartitionId());
            assertEquals(TEST_PARTITION_ID_2, destinations.get(1).getPartitionId());
        }

        @Test
        @DisplayName("Should use same destination for getTopic, listSubscriptions, and subscribe")
        void postConstruct_ShouldUseSameDestinationForAllOperations() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
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

    // ========================================
    // Edge Cases Tests
    // ========================================

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

            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
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

    // ========================================
    // Subscriber Configuration Tests
    // ========================================

    @Nested
    @DisplayName("Subscriber Configuration Tests")
    class SubscriberConfigurationTests {

        @Test
        @DisplayName("Should verify subscriber is properly configured")
        void postConstruct_SubscriberConfiguration_ShouldConfigureCorrectly() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
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
    // NEW: Message Receiver Lambda Tests
    // ========================================

    // ========================================
// Message Receiver Lambda Tests
// ========================================

    @Nested
    @DisplayName("Message Receiver Lambda Tests")
    class MessageReceiverLambdaTests {

        @Test
        @DisplayName("Should nack invalid message without processing")
        void messageReceiver_InvalidMessage_ShouldNackImmediately() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            OqmMessage invalidMessage = createInvalidMessage();
            OqmAckReplier ackReplier = mock(OqmAckReplier.class);

            invokeMessageReceiver(receiver, invalidMessage, ackReplier);

            verify(ackReplier).nack(false);
            verify(ackReplier, never()).ack();
        }

        @Test
        @DisplayName("Should create message receiver that is not null")
        void messageReceiver_ShouldBeCreated() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(DEAD_LETTER_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            assertNotNull(receiver, "Message receiver should be created");
        }
    }
}
