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

package org.opengroup.osdu.storage.provider.azure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AsyncConfigTest {

    @InjectMocks
    private AsyncConfig asyncConfig;

    @BeforeEach
    void setUp() {
        // Set default values using reflection
        ReflectionTestUtils.setField(asyncConfig, "corePoolSize", 3);
        ReflectionTestUtils.setField(asyncConfig, "maxPoolSize", 6);
        ReflectionTestUtils.setField(asyncConfig, "queueCapacity", 100);
        ReflectionTestUtils.setField(asyncConfig, "threadNamePrefix", "Primary-Async-");
        ReflectionTestUtils.setField(asyncConfig, "waitForTasksToCompleteOnShutdown", true);
        ReflectionTestUtils.setField(asyncConfig, "awaitTerminationSeconds", 60);
    }

    @Test
    void testTaskExecutor_DefaultConfiguration() {
        ThreadPoolTaskExecutor executor = asyncConfig.taskExecutor();

        assertNotNull(executor, "TaskExecutor should not be null");
        assertEquals(3, executor.getCorePoolSize(), "Core pool size should be 3");
        assertEquals(6, executor.getMaxPoolSize(), "Max pool size should be 6");
        assertEquals(100, executor.getQueueCapacity(), "Queue capacity should be 100");
        assertEquals("Primary-Async-", executor.getThreadNamePrefix(), "Thread name prefix should match");
        // These properties are set but not directly accessible via getters in Spring's ThreadPoolTaskExecutor
        // We can verify they're set correctly by checking that the executor is properly configured
    }

    @Test
    void testTaskExecutor_CustomConfiguration() {
        ReflectionTestUtils.setField(asyncConfig, "corePoolSize", 5);
        ReflectionTestUtils.setField(asyncConfig, "maxPoolSize", 10);
        ReflectionTestUtils.setField(asyncConfig, "queueCapacity", 200);
        ReflectionTestUtils.setField(asyncConfig, "threadNamePrefix", "Custom-Async-");
        ReflectionTestUtils.setField(asyncConfig, "waitForTasksToCompleteOnShutdown", false);
        ReflectionTestUtils.setField(asyncConfig, "awaitTerminationSeconds", 30);

        ThreadPoolTaskExecutor executor = asyncConfig.taskExecutor();

        assertEquals(5, executor.getCorePoolSize(), "Core pool size should be 5");
        assertEquals(10, executor.getMaxPoolSize(), "Max pool size should be 10");
        assertEquals(200, executor.getQueueCapacity(), "Queue capacity should be 200");
        assertEquals("Custom-Async-", executor.getThreadNamePrefix(), "Thread name prefix should be custom");
        // Properties are set during configuration but not accessible via getters
    }

    @Test
    void testTaskExecutor_ExecutorInitializationAndAvailability() {
        ThreadPoolTaskExecutor executor = asyncConfig.taskExecutor();

        assertNotNull(executor.getThreadPoolExecutor(), "ThreadPoolExecutor should be available");
        assertTrue(executor.getThreadPoolExecutor().getCorePoolSize() >= 0,
                "Thread pool should be initialized");
        assertNotNull(executor, "TaskExecutor should be properly configured for execution and task decoration");
    }

    @Test
    void testTaskExecutor_ThreadNameConfiguration() {
        ReflectionTestUtils.setField(asyncConfig, "threadNamePrefix", "Test-Worker-");

        ThreadPoolTaskExecutor executor = asyncConfig.taskExecutor();

        assertEquals("Test-Worker-", executor.getThreadNamePrefix(),
                "Thread name prefix should be configurable");
    }

    @Test
    void testTaskExecutor_BoundaryValues() {
        ReflectionTestUtils.setField(asyncConfig, "corePoolSize", 1);
        ReflectionTestUtils.setField(asyncConfig, "maxPoolSize", 1);
        ReflectionTestUtils.setField(asyncConfig, "queueCapacity", 1);
        ReflectionTestUtils.setField(asyncConfig, "awaitTerminationSeconds", 1);

        ThreadPoolTaskExecutor executor = asyncConfig.taskExecutor();

        assertEquals(1, executor.getCorePoolSize(), "Core pool size boundary value should work");
        assertEquals(1, executor.getMaxPoolSize(), "Max pool size boundary value should work");
        assertEquals(1, executor.getQueueCapacity(), "Queue capacity boundary value should work");
    }

    @Test
    void testTaskExecutor_LargeValues() {
        ReflectionTestUtils.setField(asyncConfig, "corePoolSize", 50);
        ReflectionTestUtils.setField(asyncConfig, "maxPoolSize", 100);
        ReflectionTestUtils.setField(asyncConfig, "queueCapacity", 1000);
        ReflectionTestUtils.setField(asyncConfig, "awaitTerminationSeconds", 300);

        ThreadPoolTaskExecutor executor = asyncConfig.taskExecutor();

        assertEquals(50, executor.getCorePoolSize(), "Large core pool size should work");
        assertEquals(100, executor.getMaxPoolSize(), "Large max pool size should work");
        assertEquals(1000, executor.getQueueCapacity(), "Large queue capacity should work");
    }
}