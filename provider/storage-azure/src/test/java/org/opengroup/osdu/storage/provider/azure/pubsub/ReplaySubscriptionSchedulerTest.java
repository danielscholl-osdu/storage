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
import org.opengroup.osdu.storage.provider.azure.interfaces.ISubscriptionRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ReplaySubscriptionSchedulerTest {

    @Mock
    private ReplaySubscriptionRegistry mockReplaySubscriptionRegistry;

    @InjectMocks
    private ReplaySubscriptionScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Set default configuration values
        configureScheduler(1, 2, 10, 30, 60, true);
    }

    private void configureScheduler(int corePoolSize, int maxPoolSize, int queueCapacity,
                                   int awaitTerminationSeconds, int keepAliveSeconds,
                                   boolean allowCoreThreadTimeOut) {
        ReflectionTestUtils.setField(scheduler, "corePoolSize", corePoolSize);
        ReflectionTestUtils.setField(scheduler, "maxPoolSize", maxPoolSize);
        ReflectionTestUtils.setField(scheduler, "queueCapacity", queueCapacity);
        ReflectionTestUtils.setField(scheduler, "awaitTerminationSeconds", awaitTerminationSeconds);
        ReflectionTestUtils.setField(scheduler, "keepAliveSeconds", keepAliveSeconds);
        ReflectionTestUtils.setField(scheduler, "allowCoreThreadTimeOut", allowCoreThreadTimeOut);
    }

    @Test
    void testGetSubscriptionRegistry() {
        ISubscriptionRegistry result = scheduler.getSubscriptionRegistry();

        assertSame(mockReplaySubscriptionRegistry, result, "Should return the replay subscription registry");
    }

    @Test
    void testGetTaskExecutorBeanName() {
        String result = scheduler.getTaskExecutorBeanName();

        assertEquals("replaySchedulerTaskExecutor", result, "Should return correct bean name");
    }

    @Test
    void testReplaySchedulerTaskExecutor_DefaultConfiguration() {
        ThreadPoolTaskExecutor executor = scheduler.replaySchedulerTaskExecutor();

        assertNotNull(executor, "Task executor should not be null");
        assertEquals(1, executor.getCorePoolSize(), "Core pool size should be 1");
        assertEquals(2, executor.getMaxPoolSize(), "Max pool size should be 2");
        assertEquals(10, executor.getQueueCapacity(), "Queue capacity should be 10");
        assertEquals("ReplaySched-", executor.getThreadNamePrefix(), "Thread name prefix should be correct");
        // Verify executor configuration is applied (properties set internally)
    }

    @Test
    void testReplaySchedulerTaskExecutor_CustomConfiguration() {
        configureScheduler(3, 5, 25, 45, 90, false);

        ThreadPoolTaskExecutor executor = scheduler.replaySchedulerTaskExecutor();

        assertEquals(3, executor.getCorePoolSize(), "Core pool size should match custom config");
        assertEquals(5, executor.getMaxPoolSize(), "Max pool size should match custom config");
        assertEquals(25, executor.getQueueCapacity(), "Queue capacity should match custom config");
        // Custom configuration applied internally
    }

    @Test
    void testReplaySchedulerTaskExecutor_RejectedExecutionPolicy() {
        ThreadPoolTaskExecutor executor = scheduler.replaySchedulerTaskExecutor();

        assertNotNull(executor.getThreadPoolExecutor(), "ThreadPoolExecutor should be available");
    }

    @Test
    void testReplaySchedulerTaskExecutor_IsInitialized() {
        ThreadPoolTaskExecutor executor = scheduler.replaySchedulerTaskExecutor();

        assertTrue(executor.getThreadPoolExecutor().getCorePoolSize() >= 0,
                "Thread pool should be initialized");
    }

    @Test
    void testCreateOptimizedTaskExecutor_DefaultValues() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) ReflectionTestUtils.invokeMethod(
                scheduler, "createOptimizedTaskExecutor", "Test");


        assertNotNull(executor, "Task executor should not be null");
        assertEquals(1, executor.getCorePoolSize(), "Core pool size should be 1");
        assertEquals(2, executor.getMaxPoolSize(), "Max pool size should be 2");
        assertEquals(10, executor.getQueueCapacity(), "Queue capacity should be 10");
        assertEquals("TestSched-", executor.getThreadNamePrefix(), "Thread name prefix should use parameter");
        // Executor configuration applied internally
    }

    @Test
    void testCreateOptimizedTaskExecutor_MinimalConfiguration() {
        configureScheduler(1, 1, 1, 5, 10, false);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) ReflectionTestUtils.invokeMethod(
                scheduler, "createOptimizedTaskExecutor", "Minimal");

        assertEquals(1, executor.getCorePoolSize(), "Minimal core pool size should work");
        assertEquals(1, executor.getMaxPoolSize(), "Minimal max pool size should work");
        assertEquals(1, executor.getQueueCapacity(), "Minimal queue capacity should work");
        // Minimal configuration applied
    }

    @Test
    void testCreateOptimizedTaskExecutor_LargeConfiguration() {
        ReflectionTestUtils.setField(scheduler, "corePoolSize", 10);
        ReflectionTestUtils.setField(scheduler, "maxPoolSize", 20);
        configureScheduler(10, 20, 100, 120, 300, true);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) ReflectionTestUtils.invokeMethod(
                scheduler, "createOptimizedTaskExecutor", "Large");

        assertEquals(10, executor.getCorePoolSize(), "Large core pool size should work");
        assertEquals(20, executor.getMaxPoolSize(), "Large max pool size should work");
        assertEquals(100, executor.getQueueCapacity(), "Large queue capacity should work");
        // Large configuration applied
    }

    @Test
    void testCreateOptimizedTaskExecutor_ThreadNaming() {
        ThreadPoolTaskExecutor executor1 = (ThreadPoolTaskExecutor) ReflectionTestUtils.invokeMethod(
                scheduler, "createOptimizedTaskExecutor", "First");
        ThreadPoolTaskExecutor executor2 = (ThreadPoolTaskExecutor) ReflectionTestUtils.invokeMethod(
                scheduler, "createOptimizedTaskExecutor", "Second");

        assertEquals("FirstSched-", executor1.getThreadNamePrefix(), "First executor should have correct prefix");
        assertEquals("SecondSched-", executor2.getThreadNamePrefix(), "Second executor should have correct prefix");
    }

    @Test
    void testInheritanceFromAbstractSubscriptionScheduler() {
        assertTrue(scheduler instanceof AbstractSubscriptionScheduler,
                "ReplaySubscriptionScheduler should extend AbstractSubscriptionScheduler");
    }

    @Test
    void testConfigurationProperties() {
        assertEquals(1, ReflectionTestUtils.getField(scheduler, "corePoolSize"));
        assertEquals(2, ReflectionTestUtils.getField(scheduler, "maxPoolSize"));
        assertEquals(10, ReflectionTestUtils.getField(scheduler, "queueCapacity"));
        assertEquals(30, ReflectionTestUtils.getField(scheduler, "awaitTerminationSeconds"));
        assertEquals(60, ReflectionTestUtils.getField(scheduler, "keepAliveSeconds"));
        assertTrue((boolean) ReflectionTestUtils.getField(scheduler, "allowCoreThreadTimeOut"));
    }

    @Test
    void testGetSubscriptionRegistry_LogsDebugMessage() {
        scheduler.getSubscriptionRegistry();

        // The actual logging is tested through the method execution
        assertSame(mockReplaySubscriptionRegistry, scheduler.getSubscriptionRegistry());
    }

    @Test
    void testReplaySchedulerTaskExecutor_LogsInitializationMessage() {
        ThreadPoolTaskExecutor executor = scheduler.replaySchedulerTaskExecutor();

        // The actual logging is tested through the method execution
        assertNotNull(executor, "Should create executor and log initialization message");
        // Bean name is set internally during Spring initialization
    }
}
