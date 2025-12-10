// Copyright © Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.microsoft.azure.servicebus.SubscriptionClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.azure.servicebus.ISubscriptionClientFactory;
import org.opengroup.osdu.storage.provider.azure.di.ServiceBusConfig;
import org.opengroup.osdu.storage.provider.azure.interfaces.ISubscriptionRegistry;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionManagerTest {

    @Mock
    private ServiceBusConfig mockServiceBusConfig;

    @Mock
    private ISubscriptionClientFactory mockSubscriptionClientFactory;

    @Mock
    private LegalComplianceChangeUpdate mockLegalComplianceChangeUpdate;

    @Mock
    private ISubscriptionRegistry mockRegistry;

    @Mock
    private SubscriptionClient mockSubscriptionClient;

    @Mock
    private LegalTagMessageHandler mockLegalTagMessageHandler;

    @Mock
    private ReplayMessageHandler mockReplayMessageHandler;

    @InjectMocks
    private SubscriptionManager subscriptionManager;

    @BeforeEach
    void setUp() {
        // Set default configuration values
        ReflectionTestUtils.setField(subscriptionManager, "cleanupAwaitTerminationSeconds", 10);
        ReflectionTestUtils.setField(subscriptionManager, "maxConcurrentCalls", 1);
        ReflectionTestUtils.setField(subscriptionManager, "autoComplete", false);
        ReflectionTestUtils.setField(subscriptionManager, "maxAutoRenewDurationMinutes", 5);
    }

    @Test
    void testSubscribeToEvents_Success() throws ServiceBusException, InterruptedException {
        when(mockRegistry.getSubscriptionType()).thenReturn("LegalTag");
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant1", "tenant2"));
        when(mockRegistry.getTopic()).thenReturn("legaltag-topic");
        when(mockRegistry.getSubscription()).thenReturn("legaltag-subscription");
        when(mockRegistry.getMessageHandler()).thenReturn(mockLegalTagMessageHandler);
        
        when(mockServiceBusConfig.getSbExecutorThreadPoolSize()).thenReturn("5");
        when(mockSubscriptionClientFactory.getClient(anyString(), anyString(), anyString())).thenReturn(mockSubscriptionClient);

        subscriptionManager.subscribeToEvents(mockRegistry);

        verify(mockRegistry, times(1)).getCurrentTenantList();
        verify(mockSubscriptionClientFactory, times(2)).getClient(anyString(), eq("legaltag-topic"), eq("legaltag-subscription"));
        verify(mockSubscriptionClient, times(2)).registerMessageHandler(any(), any(), any());
        
        Set<String> activeSubscriptions = subscriptionManager.getActiveSubscriptions("LegalTag");
        assertEquals(2, activeSubscriptions.size(), "Should have 2 active subscriptions");
        assertTrue(activeSubscriptions.contains("tenant1"), "Should contain tenant1");
        assertTrue(activeSubscriptions.contains("tenant2"), "Should contain tenant2");
    }

    @Test
    void testSubscribeToEvents_ValidationFailure() throws ServiceBusException, InterruptedException {
        when(mockRegistry.getSubscriptionType()).thenReturn("LegalTag");
        when(mockRegistry.getMessageHandler()).thenReturn(null); // Invalid handler

        subscriptionManager.subscribeToEvents(mockRegistry);

        verify(mockRegistry, never()).getCurrentTenantList();
        verify(mockSubscriptionClientFactory, never()).getClient(anyString(), anyString(), anyString());
    }

    @Test
    void testSubscribeToEvents_EmptyTenantList() throws ServiceBusException, InterruptedException {
        when(mockRegistry.getSubscriptionType()).thenReturn("LegalTag");
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList());
        when(mockRegistry.getTopic()).thenReturn("legaltag-topic");
        when(mockRegistry.getSubscription()).thenReturn("legaltag-subscription");
        when(mockRegistry.getMessageHandler()).thenReturn(mockLegalTagMessageHandler);

        subscriptionManager.subscribeToEvents(mockRegistry);

        verify(mockRegistry, times(1)).getCurrentTenantList();
        verify(mockSubscriptionClientFactory, never()).getClient(anyString(), anyString(), anyString());
    }

    @Test
    void testSubscribeToEvents_IncrementalUpdate() throws ServiceBusException, InterruptedException {
        when(mockRegistry.getSubscriptionType()).thenReturn("LegalTag");
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant1", "tenant2"));
        when(mockRegistry.getTopic()).thenReturn("legaltag-topic");
        when(mockRegistry.getSubscription()).thenReturn("legaltag-subscription");
        when(mockRegistry.getMessageHandler()).thenReturn(mockLegalTagMessageHandler);
        when(mockServiceBusConfig.getSbExecutorThreadPoolSize()).thenReturn("5");
        when(mockSubscriptionClientFactory.getClient(anyString(), anyString(), anyString())).thenReturn(mockSubscriptionClient);

        // First subscription setup
        subscriptionManager.subscribeToEvents(mockRegistry);

        // Reset mock interactions for second call
        reset(mockSubscriptionClientFactory, mockSubscriptionClient);
        when(mockSubscriptionClientFactory.getClient(anyString(), anyString(), anyString())).thenReturn(mockSubscriptionClient);

        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant2", "tenant3"));

        subscriptionManager.subscribeToEvents(mockRegistry);

        Set<String> activeSubscriptions = subscriptionManager.getActiveSubscriptions("LegalTag");
        assertEquals(2, activeSubscriptions.size(), "Should have 2 active subscriptions after update");
        assertTrue(activeSubscriptions.contains("tenant2"), "Should contain tenant2 (kept)");
        assertTrue(activeSubscriptions.contains("tenant3"), "Should contain tenant3 (added)");
        assertFalse(activeSubscriptions.contains("tenant1"), "Should not contain tenant1 (removed)");
        
        // Verify only tenant3 was added (not tenant2 which was kept)
        verify(mockSubscriptionClientFactory, times(1)).getClient(eq("tenant3"), anyString(), anyString());
    }

    @Test
    void testSubscribeToEvents_NoChanges() throws ServiceBusException, InterruptedException {
        when(mockRegistry.getSubscriptionType()).thenReturn("LegalTag");
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant1", "tenant2"));
        when(mockRegistry.getTopic()).thenReturn("legaltag-topic");
        when(mockRegistry.getSubscription()).thenReturn("legaltag-subscription");
        when(mockRegistry.getMessageHandler()).thenReturn(mockLegalTagMessageHandler);
        when(mockServiceBusConfig.getSbExecutorThreadPoolSize()).thenReturn("5");
        when(mockSubscriptionClientFactory.getClient(anyString(), anyString(), anyString())).thenReturn(mockSubscriptionClient);

        // First subscription setup
        subscriptionManager.subscribeToEvents(mockRegistry);

        // Reset mock interactions for second call
        reset(mockSubscriptionClientFactory);

        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant1", "tenant2"));

        subscriptionManager.subscribeToEvents(mockRegistry);

        verifyNoInteractions(mockSubscriptionClientFactory);
    }

    @Test
    void testSubscribeToEvents_ReplayMessageHandler() throws ServiceBusException, InterruptedException {
        when(mockRegistry.getSubscriptionType()).thenReturn("Replay");
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant1"));
        when(mockRegistry.getTopic()).thenReturn("replay-topic");
        when(mockRegistry.getSubscription()).thenReturn("replay-subscription");
        when(mockRegistry.getMessageHandler()).thenReturn(mockReplayMessageHandler);
        when(mockServiceBusConfig.getSbExecutorThreadPoolSize()).thenReturn("5");
        when(mockSubscriptionClientFactory.getClient(anyString(), anyString(), anyString())).thenReturn(mockSubscriptionClient);

        subscriptionManager.subscribeToEvents(mockRegistry);

        verify(mockSubscriptionClient, times(1)).registerMessageHandler(any(ReplaySubscriptionMessageHandler.class), any(), any());
    }

    @Test
    void testSubscribeToEvents_UnsupportedMessageHandler() throws ServiceBusException, InterruptedException {
        Object unsupportedHandler = new Object();
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant1"));
        when(mockRegistry.getTopic()).thenReturn("test-topic");
        when(mockRegistry.getSubscription()).thenReturn("test-subscription");
        when(mockRegistry.getMessageHandler()).thenReturn(unsupportedHandler);
        when(mockServiceBusConfig.getSbExecutorThreadPoolSize()).thenReturn("5");
        when(mockSubscriptionClientFactory.getClient(anyString(), anyString(), anyString())).thenReturn(mockSubscriptionClient);

        assertDoesNotThrow(() -> subscriptionManager.subscribeToEvents(mockRegistry));
        
        // Verify no message handler was registered due to unsupported type
        Set<String> activeSubscriptions = subscriptionManager.getActiveSubscriptions("Test");
        assertTrue(activeSubscriptions.isEmpty(), "Should have no active subscriptions due to handler error");
    }

    @Test
    void testSubscribeToEvents_ClientCreationFailure() throws ServiceBusException, InterruptedException {
        when(mockRegistry.getSubscriptionType()).thenReturn("LegalTag");
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant1"));
        when(mockRegistry.getTopic()).thenReturn("legaltag-topic");
        when(mockRegistry.getSubscription()).thenReturn("legaltag-subscription");
        when(mockRegistry.getMessageHandler()).thenReturn(mockLegalTagMessageHandler);
        when(mockServiceBusConfig.getSbExecutorThreadPoolSize()).thenReturn("5");
        when(mockSubscriptionClientFactory.getClient(anyString(), anyString(), anyString())).thenReturn(null);

        subscriptionManager.subscribeToEvents(mockRegistry);

        Set<String> activeSubscriptions = subscriptionManager.getActiveSubscriptions("LegalTag");
        assertTrue(activeSubscriptions.isEmpty(), "Should have no active subscriptions due to client creation failure");
    }

    @Test
    void testMarkTenantProcessing() {
        subscriptionManager.markTenantProcessingStart("LegalTag", "tenant1");
        subscriptionManager.markTenantProcessingStart("LegalTag", "tenant2");

        Map<String, Set<String>> activeProcessing = 
            (Map<String, Set<String>>) ReflectionTestUtils.getField(subscriptionManager, "activeMessageProcessing");
        
        assertNotNull(activeProcessing.get("LegalTag"), "Should have processing set for LegalTag");
        assertTrue(activeProcessing.get("LegalTag").contains("tenant1"), "Should contain tenant1");
        assertTrue(activeProcessing.get("LegalTag").contains("tenant2"), "Should contain tenant2");

        subscriptionManager.markTenantProcessingEnd("LegalTag", "tenant1");

        assertFalse(activeProcessing.get("LegalTag").contains("tenant1"), "Should not contain tenant1 after end");
        assertTrue(activeProcessing.get("LegalTag").contains("tenant2"), "Should still contain tenant2");
    }

    @Test
    void testMarkTenantProcessingEnd_NonExistentType() {
        assertDoesNotThrow(() -> subscriptionManager.markTenantProcessingEnd("NonExistent", "tenant1"));
    }

    @Test
    void testValidateDependencies_NullServiceBusConfig() {
        ReflectionTestUtils.setField(subscriptionManager, "serviceBusConfig", null);

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(subscriptionManager, "validateDependencies", mockRegistry);

        assertFalse(result, "Should return false for null serviceBusConfig");
    }

    @Test
    void testValidateDependencies_NullMessageHandler() {
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        when(mockRegistry.getMessageHandler()).thenReturn(null);

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(subscriptionManager, "validateDependencies", mockRegistry);

        assertFalse(result, "Should return false for null message handler");
    }

    @Test
    void testValidateDependencies_EmptyTopicOrSubscription() {
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        when(mockRegistry.getMessageHandler()).thenReturn(mockLegalTagMessageHandler);
        when(mockRegistry.getTopic()).thenReturn("");
        when(mockRegistry.getSubscription()).thenReturn("valid-subscription");

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(subscriptionManager, "validateDependencies", mockRegistry);

        assertFalse(result, "Should return false for empty topic");
    }

    @Test
    void testValidateDependencies_ValidConfig() {
        when(mockRegistry.getMessageHandler()).thenReturn(mockLegalTagMessageHandler);
        when(mockRegistry.getTopic()).thenReturn("valid-topic");
        when(mockRegistry.getSubscription()).thenReturn("valid-subscription");

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(subscriptionManager, "validateDependencies", mockRegistry);

        assertTrue(result, "Should return true for valid configuration");
    }

    @Test
    void testGetActiveSubscriptions_EmptyMap() {
        Set<String> result = subscriptionManager.getActiveSubscriptions("NonExistent");

        assertTrue(result.isEmpty(), "Should return empty set for non-existent subscription type");
    }

    @Test
    void testCleanup() throws ServiceBusException {
        Map<String, Map<String, SubscriptionClient>> activeClients = new ConcurrentHashMap<>();
        Map<String, SubscriptionClient> clientMap = new ConcurrentHashMap<>();
        clientMap.put("tenant1", mockSubscriptionClient);
        activeClients.put("LegalTag", clientMap);
        ReflectionTestUtils.setField(subscriptionManager, "activeSubscriptionClients", activeClients);

        subscriptionManager.cleanup();

        verify(mockSubscriptionClient, times(1)).close();
        assertTrue(activeClients.isEmpty(), "Active clients map should be cleared");
    }

    @Test
    void testCleanup_ExceptionHandling() throws ServiceBusException {
        SubscriptionClient faultyClient = mock(SubscriptionClient.class);
        doThrow(new RuntimeException("Close error")).when(faultyClient).close();

        Map<String, Map<String, SubscriptionClient>> activeClients = new ConcurrentHashMap<>();
        Map<String, SubscriptionClient> clientMap = new ConcurrentHashMap<>();
        clientMap.put("tenant1", faultyClient);
        activeClients.put("LegalTag", clientMap);
        ReflectionTestUtils.setField(subscriptionManager, "activeSubscriptionClients", activeClients);


        assertDoesNotThrow(() -> subscriptionManager.cleanup());
        
        // Verify cleanup was attempted despite exception
        verify(faultyClient, times(1)).close();
    }

    @Test
    void testConfigurationValues() {
        assertEquals(10, ReflectionTestUtils.getField(subscriptionManager, "cleanupAwaitTerminationSeconds"));
        assertEquals(1, ReflectionTestUtils.getField(subscriptionManager, "maxConcurrentCalls"));
        assertFalse((boolean) ReflectionTestUtils.getField(subscriptionManager, "autoComplete"));
        assertEquals(5, ReflectionTestUtils.getField(subscriptionManager, "maxAutoRenewDurationMinutes"));
    }
}
