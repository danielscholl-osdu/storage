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
class LegalTagSubscriptionRegistryTest {

    @Mock
    private SubscriptionManager mockSubscriptionManager;

    @Mock
    private LegalTagMessageHandler mockLegalTagMessageHandler;

    @Mock
    private ServiceBusConfig mockServiceBusConfig;

    @Mock
    private ITenantFactory mockTenantFactory;

    @Mock
    private TenantInfo mockTenantInfo1;

    @Mock
    private TenantInfo mockTenantInfo2;

    @InjectMocks
    private LegalTagSubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        // Set default property values
        ReflectionTestUtils.setField(registry, "legalTagEnabled", true);
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

        assertSame(mockLegalTagMessageHandler, result, "Should return the legal tag message handler");
    }

    @Test
    void testGetCurrentTenantList() {
        // Test successful case
        when(mockTenantInfo1.getDataPartitionId()).thenReturn("tenant1");
        when(mockTenantInfo2.getDataPartitionId()).thenReturn("tenant2");
        when(mockTenantFactory.listTenantInfo()).thenReturn(Arrays.asList(mockTenantInfo1, mockTenantInfo2));
        
        List<String> result = registry.getCurrentTenantList();
        
        assertEquals(2, result.size(), "Should return 2 tenants");
        assertTrue(result.contains("tenant1"), "Should contain tenant1");
        assertTrue(result.contains("tenant2"), "Should contain tenant2");
        verify(mockTenantFactory, times(1)).listTenantInfo();
        
        // Test with null tenants
        reset(mockTenantFactory);
        when(mockTenantInfo1.getDataPartitionId()).thenReturn("tenant1");
        when(mockTenantFactory.listTenantInfo()).thenReturn(Arrays.asList(mockTenantInfo1, null));
        
        result = registry.getCurrentTenantList();
        
        assertEquals(1, result.size(), "Should filter out null tenants");
        assertTrue(result.contains("tenant1"), "Should contain tenant1");
        
        // Test with null partition IDs
        reset(mockTenantFactory);
        when(mockTenantInfo1.getDataPartitionId()).thenReturn("tenant1");
        when(mockTenantInfo2.getDataPartitionId()).thenReturn(null);
        when(mockTenantFactory.listTenantInfo()).thenReturn(Arrays.asList(mockTenantInfo1, mockTenantInfo2));
        
        result = registry.getCurrentTenantList();
        
        assertEquals(1, result.size(), "Should filter out tenants with null partition IDs");
        assertTrue(result.contains("tenant1"), "Should contain tenant1");
        
        // Test with empty partition IDs
        reset(mockTenantFactory);
        when(mockTenantInfo1.getDataPartitionId()).thenReturn("tenant1");
        when(mockTenantInfo2.getDataPartitionId()).thenReturn("  ");
        when(mockTenantFactory.listTenantInfo()).thenReturn(Arrays.asList(mockTenantInfo1, mockTenantInfo2));
        
        result = registry.getCurrentTenantList();
        
        assertEquals(1, result.size(), "Should filter out tenants with empty/whitespace partition IDs");
        assertTrue(result.contains("tenant1"), "Should contain tenant1");
        
        // Test exceptions
        reset(mockTenantFactory);
        when(mockTenantFactory.listTenantInfo())
                .thenThrow(new IllegalStateException("ThreadScope not available"));
        
        result = registry.getCurrentTenantList();
        
        assertTrue(result.isEmpty(), "Should return empty list for ThreadScope exception");
        
        reset(mockTenantFactory);
        when(mockTenantFactory.listTenantInfo())
                .thenThrow(new RuntimeException("General error"));
        
        result = registry.getCurrentTenantList();
        
        assertTrue(result.isEmpty(), "Should return empty list for general exceptions");
    }

    @Test
    void testSimpleGetters() {
        // Test getSubscriptionType
        assertEquals("LegalTag", registry.getSubscriptionType(), "Should return LegalTag subscription type");
        
        // Test getFeatureFlagProperty  
        assertEquals("azure.feature.legaltag-compliance-update.enabled", registry.getFeatureFlagProperty(),
                "Should return correct feature flag property");
        
        // Test getSchedulerPropertyPrefix
        assertEquals("legaltag.subscription", registry.getSchedulerPropertyPrefix(),
                "Should return correct scheduler property prefix");
    }

    @Test
    void testIsEnabled() {
        // Test enabled case
        ReflectionTestUtils.setField(registry, "legalTagEnabled", true);
        assertTrue(registry.isEnabled(), "Should return true when legal tag is enabled");
        
        // Test disabled case
        ReflectionTestUtils.setField(registry, "legalTagEnabled", false);
        assertFalse(registry.isEnabled(), "Should return false when legal tag is disabled");
        
        // Test null case
        ReflectionTestUtils.setField(registry, "legalTagEnabled", null);
        assertFalse(registry.isEnabled(), "Should return false when legal tag enabled is null");
    }

    @Test
    void testIsSchedulerEnabled() {
        // Test both enabled
        ReflectionTestUtils.setField(registry, "legalTagEnabled", true);
        ReflectionTestUtils.setField(registry, "schedulerEnabled", true);
        assertTrue(registry.isSchedulerEnabled(), "Should return true when both legal tag and scheduler are enabled");
        
        // Test legal tag disabled
        ReflectionTestUtils.setField(registry, "legalTagEnabled", false);
        ReflectionTestUtils.setField(registry, "schedulerEnabled", true);
        assertFalse(registry.isSchedulerEnabled(), "Should return false when legal tag is disabled");
        
        // Test scheduler disabled
        ReflectionTestUtils.setField(registry, "legalTagEnabled", true);
        ReflectionTestUtils.setField(registry, "schedulerEnabled", false);
        assertFalse(registry.isSchedulerEnabled(), "Should return false when scheduler is disabled");
        
        // Test both null
        ReflectionTestUtils.setField(registry, "legalTagEnabled", null);
        ReflectionTestUtils.setField(registry, "schedulerEnabled", null);
        assertFalse(registry.isSchedulerEnabled(), "Should return false when both are null");
    }

    @Test
    void testServiceBusGetters() {
        // Test getTopic
        when(mockServiceBusConfig.getLegalServiceBusTopic()).thenReturn("legal-topic");
        assertEquals("legal-topic", registry.getTopic(), "Should return legal service bus topic");
        verify(mockServiceBusConfig, times(1)).getLegalServiceBusTopic();
        
        // Test getSubscription
        when(mockServiceBusConfig.getLegalServiceBusTopicSubscription()).thenReturn("legal-subscription");
        assertEquals("legal-subscription", registry.getSubscription(), "Should return legal service bus subscription");
        verify(mockServiceBusConfig, times(1)).getLegalServiceBusTopicSubscription();
    }

    @Test
    void testGetActiveSubscriptions() {
        // Test with active subscriptions
        Set<String> expectedSubscriptions = new HashSet<>(Arrays.asList("tenant1", "tenant2"));
        when(mockSubscriptionManager.getActiveSubscriptions("LegalTag")).thenReturn(expectedSubscriptions);
        
        Set<String> result = registry.getActiveSubscriptions();
        
        assertEquals(expectedSubscriptions, result, "Should return active subscriptions from manager");
        verify(mockSubscriptionManager, times(1)).getActiveSubscriptions("LegalTag");
        
        // Test with empty subscriptions
        when(mockSubscriptionManager.getActiveSubscriptions("LegalTag")).thenReturn(new HashSet<>());
        
        result = registry.getActiveSubscriptions();
        
        assertTrue(result.isEmpty(), "Should return empty set when no active subscriptions");
        verify(mockSubscriptionManager, times(2)).getActiveSubscriptions("LegalTag");
    }
}
