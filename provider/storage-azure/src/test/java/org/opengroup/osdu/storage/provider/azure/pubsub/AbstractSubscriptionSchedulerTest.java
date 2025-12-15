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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.storage.provider.azure.interfaces.ISubscriptionRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractSubscriptionSchedulerTest {

    @Mock
    private ISubscriptionRegistry mockRegistry;

    private TestableAbstractSubscriptionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TestableAbstractSubscriptionScheduler(mockRegistry);
        
        // Set default configuration values
        ReflectionTestUtils.setField(scheduler, "refreshIntervalMs", 300000L);
        ReflectionTestUtils.setField(scheduler, "initialDelayMs", 300000L);
        ReflectionTestUtils.setField(scheduler, "baseCorePoolSize", 1);
        ReflectionTestUtils.setField(scheduler, "baseMaxPoolSize", 2);
        ReflectionTestUtils.setField(scheduler, "baseQueueCapacity", 10);
        ReflectionTestUtils.setField(scheduler, "baseAwaitTerminationSeconds", 30);
    }

    @Test
    void testInitialize() {
        // Test with enabled scheduler
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        when(mockRegistry.isSchedulerEnabled()).thenReturn(true);
        
        scheduler.initialize();
        
        verify(mockRegistry, times(1)).getSubscriptionType();
        verify(mockRegistry, times(1)).isSchedulerEnabled();
        
        // Test with disabled scheduler
        reset(mockRegistry);
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        when(mockRegistry.isSchedulerEnabled()).thenReturn(false);
        
        scheduler.initialize();
        
        verify(mockRegistry, times(1)).getSubscriptionType();
        verify(mockRegistry, times(1)).isSchedulerEnabled();
    }

    @Test
    void testScheduledTenantRefresh_SchedulerDisabled() {
        when(mockRegistry.isSchedulerEnabled()).thenReturn(false);

        scheduler.scheduledTenantRefresh();

        verify(mockRegistry, times(1)).isSchedulerEnabled();
        verify(mockRegistry, never()).subscribeToEvents();
    }

    @Test
    void testScheduledTenantRefresh_RefreshInProgress() {
        when(mockRegistry.isSchedulerEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(scheduler, "refreshInProgress", 
                ReflectionTestUtils.getField(scheduler, "refreshInProgress"));
        scheduler.refreshInProgress.set(true);

        scheduler.scheduledTenantRefresh();

        verify(mockRegistry, times(1)).isSchedulerEnabled();
        verify(mockRegistry, never()).subscribeToEvents();
    }

    @Test
    void testScheduledTenantRefresh_NoTenants() {
        when(mockRegistry.isSchedulerEnabled()).thenReturn(true);
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList());

        scheduler.scheduledTenantRefresh();

        verify(mockRegistry, times(1)).getCurrentTenantList();
        verify(mockRegistry, never()).subscribeToEvents();
    }

    @Test
    void testScheduledTenantRefresh_InitialSetup() {
        when(mockRegistry.isSchedulerEnabled()).thenReturn(true);
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant1", "tenant2"));

        scheduler.scheduledTenantRefresh();

        verify(mockRegistry, times(1)).getCurrentTenantList();
        verify(mockRegistry, times(1)).subscribeToEvents();
        assertTrue(scheduler.initialSetupCompleted.get(), "Initial setup should be marked as completed");
        assertTrue(scheduler.lastRefreshTime.get() > 0, "Last refresh time should be set");
        assertEquals(1, scheduler.refreshCount.get(), "Refresh count should be incremented");
    }

    @Test
    void testScheduledTenantRefresh_TenantChanges() {
        when(mockRegistry.isSchedulerEnabled()).thenReturn(true);
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        
        // Set initial tenant set
        Set<String> initialTenants = ConcurrentHashMap.newKeySet();
        initialTenants.add("tenant1");
        ReflectionTestUtils.setField(scheduler, "currentTenantSet", initialTenants);
        scheduler.initialSetupCompleted.set(true);
        
        // Mock new tenant list
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant1", "tenant2"));

        scheduler.scheduledTenantRefresh();

        verify(mockRegistry, times(1)).subscribeToEvents();
        assertEquals(2, scheduler.currentTenantSet.size(), "Tenant set should be updated");
        assertTrue(scheduler.currentTenantSet.contains("tenant1"), "Should contain tenant1");
        assertTrue(scheduler.currentTenantSet.contains("tenant2"), "Should contain tenant2");
    }

    @Test
    void testScheduledTenantRefresh_ExceptionHandling() {
        when(mockRegistry.isSchedulerEnabled()).thenReturn(true);
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        when(mockRegistry.getCurrentTenantList()).thenThrow(new RuntimeException("Test exception"));

        assertDoesNotThrow(() -> scheduler.scheduledTenantRefresh());
        
        assertFalse(scheduler.refreshInProgress.get(), "Refresh should not be in progress after exception");
    }

    @Test
    void testHasTenantsChanged() {
        Set<String> current = ConcurrentHashMap.newKeySet();
        current.addAll(Arrays.asList("tenant1", "tenant2"));
        ReflectionTestUtils.setField(scheduler, "currentTenantSet", current);
        
        // Test identical sets
        Set<String> identical = new HashSet<>(Arrays.asList("tenant1", "tenant2"));
        assertFalse(scheduler.hasTenantsChanged(identical), "Should not detect changes for identical tenant sets");
        
        // Test different size
        Set<String> smaller = new HashSet<>(Arrays.asList("tenant1"));
        assertTrue(scheduler.hasTenantsChanged(smaller), "Should detect changes for different sized tenant sets");
        
        // Test different content but same size
        Set<String> differentContent = new HashSet<>(Arrays.asList("tenant1", "tenant3"));
        assertTrue(scheduler.hasTenantsChanged(differentContent), "Should detect changes for different tenant content");
    }

    @Test
    void testTriggerRefresh() {
        // Test normal trigger refresh
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        when(mockRegistry.isSchedulerEnabled()).thenReturn(true);
        when(mockRegistry.getCurrentTenantList()).thenReturn(Arrays.asList("tenant1"));
        
        scheduler.triggerRefresh();
        
        verify(mockRegistry, atLeast(1)).getSubscriptionType();
        verify(mockRegistry, atLeast(1)).getCurrentTenantList();
        assertEquals(1, scheduler.currentTenantSet.size(), "Current tenant set should be updated with latest tenants");
        assertTrue(scheduler.currentTenantSet.contains("tenant1"), "Should contain the tenant from mock");
        
        // Test trigger refresh when already in progress
        reset(mockRegistry);
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        scheduler.refreshInProgress.set(true);
        
        scheduler.triggerRefresh();
        
        verify(mockRegistry, times(1)).getSubscriptionType();
        // Should not trigger actual refresh due to refresh in progress
    }

    @Test
    void testGetCurrentTenantSet() {
        // Test successful case
        List<String> tenants = Arrays.asList("tenant1", "tenant2");
        when(mockRegistry.getCurrentTenantList()).thenReturn(tenants);
        
        Set<String> result = scheduler.getCurrentTenantSet();
        
        assertNotNull(result, "Result should not be null");
        assertEquals(2, result.size(), "Should contain correct number of tenants");
        assertTrue(result.contains("tenant1"), "Should contain tenant1");
        assertTrue(result.contains("tenant2"), "Should contain tenant2");
        
        // Reset mock and test ThreadScope exception
        reset(mockRegistry);
        when(mockRegistry.getCurrentTenantList())
                .thenThrow(new IllegalStateException("ThreadScope not available"));
        
        result = scheduler.getCurrentTenantSet();
        
        assertNotNull(result, "Should return empty set for ThreadScope exception");
        assertTrue(result.isEmpty(), "Should return empty set for ThreadScope exception");
        
        // Reset mock and test general exception
        reset(mockRegistry);
        when(mockRegistry.getCurrentTenantList())
                .thenThrow(new RuntimeException("General error"));
        
        result = scheduler.getCurrentTenantSet();
        
        assertNull(result, "Should return null for general exceptions");
    }

    @Test
    void testCreateTaskExecutor() {
        // Test with default configuration
        ThreadPoolTaskExecutor executor = scheduler.createTaskExecutor("Test");
        
        assertNotNull(executor, "Task executor should not be null");
        assertEquals(1, executor.getCorePoolSize(), "Core pool size should match default configuration");
        assertEquals(2, executor.getMaxPoolSize(), "Max pool size should match default configuration");
        assertEquals(10, executor.getQueueCapacity(), "Queue capacity should match default configuration");
        assertEquals("TestScheduler-", executor.getThreadNamePrefix(), "Thread name prefix should be correct");
        
        // Test with custom configuration
        ReflectionTestUtils.setField(scheduler, "baseCorePoolSize", 3);
        ReflectionTestUtils.setField(scheduler, "baseMaxPoolSize", 6);
        ReflectionTestUtils.setField(scheduler, "baseQueueCapacity", 50);
        ReflectionTestUtils.setField(scheduler, "baseAwaitTerminationSeconds", 60);
        
        executor = scheduler.createTaskExecutor("Custom");
        
        assertEquals(3, executor.getCorePoolSize(), "Core pool size should match custom configuration");
        assertEquals(6, executor.getMaxPoolSize(), "Max pool size should match custom configuration");
        assertEquals(50, executor.getQueueCapacity(), "Queue capacity should match custom configuration");
        assertEquals("CustomScheduler-", executor.getThreadNamePrefix(), "Thread name prefix should be custom");
    }

    @Test
    void testCleanup() {
        when(mockRegistry.getSubscriptionType()).thenReturn("Test");
        scheduler.refreshInProgress.set(true);

        scheduler.cleanup();

        assertFalse(scheduler.refreshInProgress.get(), "Refresh in progress should be reset");
        verify(mockRegistry, times(1)).getSubscriptionType();
    }

    // Test implementation of AbstractSubscriptionScheduler
    private static class TestableAbstractSubscriptionScheduler extends AbstractSubscriptionScheduler {
        private final ISubscriptionRegistry registry;

        public TestableAbstractSubscriptionScheduler(ISubscriptionRegistry registry) {
            this.registry = registry;
        }

        @Override
        protected ISubscriptionRegistry getSubscriptionRegistry() {
            return registry;
        }

        @Override
        protected String getTaskExecutorBeanName() {
            return "testTaskExecutor";
        }
    }
}
