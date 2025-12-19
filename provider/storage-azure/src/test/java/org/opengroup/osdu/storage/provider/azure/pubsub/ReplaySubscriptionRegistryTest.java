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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.storage.provider.azure.di.ServiceBusConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplaySubscriptionRegistryTest {

    @Mock
    private SubscriptionManager mockSubscriptionManager;

    @Mock
    private ReplayMessageHandler mockReplayMessageHandler;

    @Mock
    private ServiceBusConfig mockServiceBusConfig;

    @Mock
    private ITenantFactory mockTenantFactory;

    @Mock
    private TenantInfo mockTenantInfo1;

    @Mock
    private TenantInfo mockTenantInfo2;

    @InjectMocks
    private ReplaySubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        // Set default property values
        ReflectionTestUtils.setField(registry, "replayEnabled", true);
        ReflectionTestUtils.setField(registry, "schedulerEnabled", true);
    }

    @Test
    void testSubscribeToEvents() {
        registry.subscribeToEvents();

        verify(mockSubscriptionManager, times(1)).subscribeToEvents(registry);
    }

    @Test
    void testGetMessageHandler() {
        Object result = registry.getMessageHandler();

        assertSame(mockReplayMessageHandler, result, "Should return the replay message handler");
    }

    @Test
    void testGetCurrentTenantList_Success() {
        when(mockTenantInfo1.getDataPartitionId()).thenReturn("tenant1");
        when(mockTenantInfo2.getDataPartitionId()).thenReturn("tenant2");
        when(mockTenantFactory.listTenantInfo()).thenReturn(Arrays.asList(mockTenantInfo1, mockTenantInfo2));

        List<String> result = registry.getCurrentTenantList();

        assertEquals(2, result.size(), "Should return 2 tenants");
        assertTrue(result.contains("tenant1"), "Should contain tenant1");
        assertTrue(result.contains("tenant2"), "Should contain tenant2");
        verify(mockTenantFactory, times(1)).listTenantInfo();
    }

    @Test
    void testGetCurrentTenantList_WithNullTenants() {
        when(mockTenantInfo1.getDataPartitionId()).thenReturn("tenant1");
        when(mockTenantFactory.listTenantInfo()).thenReturn(Arrays.asList(mockTenantInfo1, null));

        List<String> result = registry.getCurrentTenantList();

        assertEquals(1, result.size(), "Should filter out null tenants");
        assertTrue(result.contains("tenant1"), "Should contain tenant1");
    }

    @Test
    void testGetCurrentTenantList_WithNullPartitionIds() {
        when(mockTenantInfo1.getDataPartitionId()).thenReturn("tenant1");
        when(mockTenantInfo2.getDataPartitionId()).thenReturn(null);
        when(mockTenantFactory.listTenantInfo()).thenReturn(Arrays.asList(mockTenantInfo1, mockTenantInfo2));

        List<String> result = registry.getCurrentTenantList();

        assertEquals(1, result.size(), "Should filter out tenants with null partition IDs");
        assertTrue(result.contains("tenant1"), "Should contain tenant1");
    }

    @Test
    void testGetCurrentTenantList_WithEmptyPartitionIds() {
        when(mockTenantInfo1.getDataPartitionId()).thenReturn("tenant1");
        when(mockTenantInfo2.getDataPartitionId()).thenReturn("  "); // Empty/whitespace
        when(mockTenantFactory.listTenantInfo()).thenReturn(Arrays.asList(mockTenantInfo1, mockTenantInfo2));

        List<String> result = registry.getCurrentTenantList();

        assertEquals(1, result.size(), "Should filter out tenants with empty/whitespace partition IDs");
        assertTrue(result.contains("tenant1"), "Should contain tenant1");
    }

    @Test
    void testGetCurrentTenantList_ThreadScopeException() {
        when(mockTenantFactory.listTenantInfo())
                .thenThrow(new IllegalStateException("ThreadScope not available"));

        List<String> result = registry.getCurrentTenantList();

        assertTrue(result.isEmpty(), "Should return empty list for ThreadScope exception");
    }

    @Test
    void testGetCurrentTenantList_GeneralIllegalStateException() {
        when(mockTenantFactory.listTenantInfo())
                .thenThrow(new IllegalStateException("General state error"));

        List<String> result = registry.getCurrentTenantList();

        assertTrue(result.isEmpty(), "Should return empty list for general IllegalStateException");
    }

    @Test
    void testGetCurrentTenantList_GeneralException() {
        when(mockTenantFactory.listTenantInfo())
                .thenThrow(new RuntimeException("General error"));

        List<String> result = registry.getCurrentTenantList();

        assertTrue(result.isEmpty(), "Should return empty list for general exceptions");
    }

    @Test
    void testGetSubscriptionType() {
        String result = registry.getSubscriptionType();

        assertEquals("Replay", result, "Should return Replay subscription type");
    }

    @Test
    void testIsEnabled_True() {
        ReflectionTestUtils.setField(registry, "replayEnabled", true);

        boolean result = registry.isEnabled();

        assertTrue(result, "Should return true when replay is enabled");
    }

    @Test
    void testIsEnabled_False() {
        ReflectionTestUtils.setField(registry, "replayEnabled", false);

        boolean result = registry.isEnabled();

        assertFalse(result, "Should return false when replay is disabled");
    }

    @Test
    void testIsEnabled_Null() {
        ReflectionTestUtils.setField(registry, "replayEnabled", null);

        boolean result = registry.isEnabled();

        assertFalse(result, "Should return false when replay enabled is null");
    }

    @Test
    void testIsSchedulerEnabled_BothTrue() {
        ReflectionTestUtils.setField(registry, "replayEnabled", true);
        ReflectionTestUtils.setField(registry, "schedulerEnabled", true);

        boolean result = registry.isSchedulerEnabled();

        assertTrue(result, "Should return true when both replay and scheduler are enabled");
    }

    @Test
    void testIsSchedulerEnabled_ReplayDisabled() {
        ReflectionTestUtils.setField(registry, "replayEnabled", false);
        ReflectionTestUtils.setField(registry, "schedulerEnabled", true);

        boolean result = registry.isSchedulerEnabled();

        assertFalse(result, "Should return false when replay is disabled");
    }

    @Test
    void testIsSchedulerEnabled_SchedulerDisabled() {
        ReflectionTestUtils.setField(registry, "replayEnabled", true);
        ReflectionTestUtils.setField(registry, "schedulerEnabled", false);

        boolean result = registry.isSchedulerEnabled();

        assertFalse(result, "Should return false when scheduler is disabled");
    }

    @Test
    void testIsSchedulerEnabled_BothNull() {
        ReflectionTestUtils.setField(registry, "replayEnabled", null);
        ReflectionTestUtils.setField(registry, "schedulerEnabled", null);

        boolean result = registry.isSchedulerEnabled();

        assertFalse(result, "Should return false when both are null");
    }

    @Test
    void testGetFeatureFlagProperty() {
        String result = registry.getFeatureFlagProperty();

        assertEquals("feature.replay.enabled", result,
                "Should return correct feature flag property");
    }

    @Test
    void testGetSchedulerPropertyPrefix() {
        String result = registry.getSchedulerPropertyPrefix();

        assertEquals("replay.subscription", result,
                "Should return correct scheduler property prefix");
    }

    @Test
    void testGetTopic() {
        when(mockServiceBusConfig.getReplayTopic()).thenReturn("replay-topic");

        String result = registry.getTopic();

        assertEquals("replay-topic", result, "Should return replay topic");
        verify(mockServiceBusConfig, times(1)).getReplayTopic();
    }

    @Test
    void testGetSubscription() {
        when(mockServiceBusConfig.getReplaySubscription()).thenReturn("replay-subscription");

        String result = registry.getSubscription();

        assertEquals("replay-subscription", result, "Should return replay subscription");
        verify(mockServiceBusConfig, times(1)).getReplaySubscription();
    }

    @Test
    void testGetActiveSubscriptions() {
        Set<String> expectedSubscriptions = new HashSet<>(Arrays.asList("tenant1", "tenant2"));
        when(mockSubscriptionManager.getActiveSubscriptions("Replay")).thenReturn(expectedSubscriptions);

        Set<String> result = registry.getActiveSubscriptions();

        assertEquals(expectedSubscriptions, result, "Should return active subscriptions from manager");
        verify(mockSubscriptionManager, times(1)).getActiveSubscriptions("Replay");
    }

    @Test
    void testGetActiveSubscriptions_Empty() {
        when(mockSubscriptionManager.getActiveSubscriptions("Replay")).thenReturn(new HashSet<>());

        Set<String> result = registry.getActiveSubscriptions();

        assertTrue(result.isEmpty(), "Should return empty set when no active subscriptions");
        verify(mockSubscriptionManager, times(1)).getActiveSubscriptions("Replay");
    }
}
