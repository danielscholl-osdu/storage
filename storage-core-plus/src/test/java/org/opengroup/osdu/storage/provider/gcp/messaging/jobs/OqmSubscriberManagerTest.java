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
package org.opengroup.osdu.storage.provider.gcp.messaging.jobs;

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
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagConsistencyValidator;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.*;
import org.opengroup.osdu.storage.provider.gcp.messaging.config.MessagingConfigurationProperties;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadDpsHeaders;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("OqmSubscriberManager Tests")
class OqmSubscriberManagerTest {
    private static final String TEST_PARTITION_ID = "test-partition";
    private static final String TEST_PARTITION_ID_2 = "test-partition-2";
    private static final String LEGAL_TAGS_CHANGED_TOPIC = "legal-tags-changed-topic";
    private static final String LEGAL_TAGS_CHANGED_SUBSCRIPTION = "legal-tags-changed-subscription";
    private static final String TEST_MESSAGE_DATA = "{\"changedTagNames\":[\"tag1\",\"tag2\"]}";
    private static final String TEST_MESSAGE_ID = "message-id-123";

    @Mock
    private MessagingConfigurationProperties configurationProperties;

    @Mock
    private ITenantFactory tenantInfoFactory;

    @Mock
    private OqmDriver driver;

    @Mock
    private LegalTagConsistencyValidator legalTagConsistencyValidator;

    @Mock
    private LegalComplianceChangeServiceGcpImpl legalComplianceChangeServiceGcp;

    @Mock
    private ThreadDpsHeaders dpsHeaders;

    @InjectMocks
    private OqmSubscriberManager manager;

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
        lenient().when(configurationProperties.getLegalTagsChangedTopicName()).thenReturn(LEGAL_TAGS_CHANGED_TOPIC);
        lenient().when(configurationProperties.getLegalTagsChangedSubscriptionName()).thenReturn(LEGAL_TAGS_CHANGED_SUBSCRIPTION);
        lenient().when(tenantInfo1.getDataPartitionId()).thenReturn(TEST_PARTITION_ID);
        lenient().when(tenantInfo2.getDataPartitionId()).thenReturn(TEST_PARTITION_ID_2);
        lenient().when(topic.getName()).thenReturn(LEGAL_TAGS_CHANGED_TOPIC);
        lenient().when(subscription.getName()).thenReturn(LEGAL_TAGS_CHANGED_SUBSCRIPTION);
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
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class));
            verify(driver).listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class));
            verify(driver).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
        }

        @Test
        @DisplayName("Should successfully provision multiple tenants")
        void postConstruct_MultipleTenants_ShouldProvisionSuccessfully() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver, times(2)).getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class));
            verify(driver, times(2)).listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class));
            verify(driver, times(2)).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
        }

        @Test
        @DisplayName("Should register subscriber with correct configuration")
        void postConstruct_RegisterSubscriber_ShouldHaveCorrectConfiguration() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).listSubscriptions(eq(topic), queryCaptor.capture(), any(OqmDestination.class));
            OqmSubscriptionQuery capturedQuery = queryCaptor.getValue();

            assertEquals(LEGAL_TAGS_CHANGED_SUBSCRIPTION, capturedQuery.getNamePrefix());
            assertTrue(capturedQuery.getSubscriberable());
        }

        @Test
        @DisplayName("Should verify subscriber is registered for each tenant")
        void postConstruct_MultipleTenants_ShouldRegisterMultipleSubscribers() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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
    // PostConstruct - Topic Validation Tests
    // ========================================

    @Nested
    @DisplayName("PostConstruct - Topic Validation Tests")
    class PostConstructTopicValidationTests {

        @Test
        @DisplayName("Should throw AppException when topic does not exist")
        void postConstruct_TopicNotExists_ShouldThrowAppException() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertEquals("Required topic not exists.", exception.getError().getReason());
            assertTrue(exception.getError().getMessage().contains(LEGAL_TAGS_CHANGED_TOPIC));
            assertTrue(exception.getError().getMessage().contains(TEST_PARTITION_ID));
        }

        @Test
        @DisplayName("Should validate topic exists before checking subscription")
        void postConstruct_TopicNotExists_ShouldNotCheckSubscription() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.empty());

            assertThrows(AppException.class, () -> manager.postConstruct());

            verify(driver, never()).listSubscriptions(any(), any(), any());
        }

        @Test
        @DisplayName("Should fail on first tenant when topic missing")
        void postConstruct_MultipleTenants_FirstTenantTopicMissing_ShouldFailImmediately() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.emptyList());

            AppException exception = assertThrows(AppException.class, () -> manager.postConstruct());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
            assertEquals("Required subscription not exists.", exception.getError().getReason());
            assertTrue(exception.getError().getMessage().contains(LEGAL_TAGS_CHANGED_SUBSCRIPTION));
            assertTrue(exception.getError().getMessage().contains(TEST_PARTITION_ID));
        }

        @Test
        @DisplayName("Should not register subscriber when subscription missing")
        void postConstruct_SubscriptionNotExists_ShouldNotRegisterSubscriber() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), destinationCaptor.capture());
            OqmDestination capturedDestination = destinationCaptor.getValue();
            assertEquals(TEST_PARTITION_ID, capturedDestination.getPartitionId());
        }

        @Test
        @DisplayName("Should create different destinations for different tenants")
        void postConstruct_MultipleTenants_ShouldCreateDifferentDestinations() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver, times(2)).getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), destinationCaptor.capture());
            List<OqmDestination> destinations = destinationCaptor.getAllValues();

            assertEquals(2, destinations.size());
            assertEquals(TEST_PARTITION_ID, destinations.get(0).getPartitionId());
            assertEquals(TEST_PARTITION_ID_2, destinations.get(1).getPartitionId());
        }

        @Test
        @DisplayName("Should use same destination for getTopic, listSubscriptions, and subscribe")
        void postConstruct_ShouldUseSameDestinationForAllOperations() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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

            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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
        @DisplayName("Should create independent subscribers for each tenant")
        void postConstruct_MultipleTenants_ShouldCreateIndependentSubscribers() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Arrays.asList(tenantInfo1, tenantInfo2));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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
    // Message Receiver Lambda Tests
    // ========================================

    @Nested
    @DisplayName("Message Receiver Lambda Tests")
    class MessageReceiverLambdaTests {

        @Test
        @DisplayName("Should nack message when processing throws exception")
        void messageReceiver_ProcessingThrowsException_ShouldNack() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
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

            invokeMessageReceiver(receiver, message, ackReplier);

            verify(ackReplier).nack(false);
            verify(ackReplier, never()).ack();
        }

        @Test
        @DisplayName("Should attempt to process message")
        void messageReceiver_ShouldAttemptProcessing() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            OqmMessage message = createValidMessage();
            OqmAckReplier ackReplier = mock(OqmAckReplier.class);

            invokeMessageReceiver(receiver, message, ackReplier);

            // Verify processing was attempted
            verify(dpsHeaders).setThreadContext(message.getAttributes());
            verify(message, atLeastOnce()).getData();
            verify(message, atLeastOnce()).getAttributes();
            verify(message, atLeastOnce()).getId();
        }

        @Test
        @DisplayName("Should extract message data and attributes")
        void messageReceiver_ShouldExtractMessageDetails() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            OqmMessage message = createValidMessage();
            OqmAckReplier ackReplier = mock(OqmAckReplier.class);

            invokeMessageReceiver(receiver, message, ackReplier);

            verify(message, atLeastOnce()).getData();
            verify(message, atLeastOnce()).getAttributes();
            verify(message, atLeastOnce()).getId();
        }

        @Test
        @DisplayName("Should set thread context with message attributes")
        void messageReceiver_ShouldSetThreadContext() throws Exception {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            OqmMessage message = createValidMessage();
            OqmAckReplier ackReplier = mock(OqmAckReplier.class);

            invokeMessageReceiver(receiver, message, ackReplier);

            ArgumentCaptor<Map<String, String>> attributesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(dpsHeaders).setThreadContext(attributesCaptor.capture());

            Map<String, String> capturedAttributes = attributesCaptor.getValue();
            assertEquals(TEST_PARTITION_ID, capturedAttributes.get("partition-id"));
        }

        @Test
        @DisplayName("Should create message receiver that is not null")
        void messageReceiver_ShouldBeCreated() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
            OqmMessageReceiver receiver = subscriberCaptor.getValue().getMessageReceiver();

            assertNotNull(receiver, "Message receiver should be created");
        }
    }

    // ========================================
    // Configuration Property Tests
    // ========================================

    @Nested
    @DisplayName("Configuration Property Tests")
    class ConfigurationPropertyTests {

        @Test
        @DisplayName("Should use legal tags changed topic from configuration")
        void postConstruct_ShouldUseTopicFromConfiguration() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class));
            verify(configurationProperties, atLeastOnce()).getLegalTagsChangedTopicName();
        }

        @Test
        @DisplayName("Should use legal tags changed subscription from configuration")
        void postConstruct_ShouldUseSubscriptionFromConfiguration() {
            when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo1));
            when(driver.getTopic(eq(LEGAL_TAGS_CHANGED_TOPIC), any(OqmDestination.class)))
                    .thenReturn(Optional.of(topic));
            when(driver.listSubscriptions(eq(topic), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                    .thenReturn(Collections.singletonList(subscription));

            manager.postConstruct();

            verify(driver).listSubscriptions(eq(topic), queryCaptor.capture(), any(OqmDestination.class));
            assertEquals(LEGAL_TAGS_CHANGED_SUBSCRIPTION, queryCaptor.getValue().getNamePrefix());
            verify(configurationProperties, atLeastOnce()).getLegalTagsChangedSubscriptionName();
        }
    }
}
