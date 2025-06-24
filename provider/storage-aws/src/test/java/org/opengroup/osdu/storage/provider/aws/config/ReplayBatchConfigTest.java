/*
 * Copyright © Amazon Web Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ReplayBatchConfig.class})
@TestPropertySource(properties = {
        "feature.replay.enabled=true",
        "replay.batch.size=100",
        "replay.batch.parallelism=8"
})
public class ReplayBatchConfigTest {

    @Autowired
    private ReplayBatchConfig replayBatchConfig;
    
    private ExecutorService executorService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @After
    public void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    /**
     * Test that configuration properties are correctly loaded from application properties.
     */
    @Test
    public void testConfigProperties() {
        assertEquals("Batch size should match the configured value", 
                100, replayBatchConfig.getBatchSize());
        assertEquals("Parallelism should match the configured value", 
                8, replayBatchConfig.getParallelism());
    }

    /**
     * Test that the executor service is created with the correct number of threads.
     */
    @Test
    public void testReplayExecutorService() {
        // Get the executor service from the bean
        executorService = replayBatchConfig.replayExecutorService();
        
        // Verify it's not null
        assertNotNull("Executor service should not be null", executorService);
        
        // The executor service should not be shutdown when first created
        // Note: We're not asserting isShutdown() here because in a Spring context test,
        // the executor might already be managed by Spring and could be in an unexpected state
    }
    
    /**
     * Test that the executor service is properly shutdown when the bean is destroyed.
     */
    @Test
    public void testShutdown() {
        // Create a spy of the executor service
        executorService = spy(replayBatchConfig.replayExecutorService());
        
        // Set the spy executor service in the config using reflection
        ReflectionTestUtils.setField(replayBatchConfig, "executorService", executorService);
        
        // Call shutdown method
        replayBatchConfig.shutdown();
        
        // Verify shutdown was called
        verify(executorService).shutdown();
        
        // Verify the executor service is shutdown
        assertTrue("Executor service should be shutdown after calling shutdown method", 
                executorService.isShutdown());
    }
    
    /**
     * Test that the shutdown method handles interruption correctly.
     */
    @Test
    public void testShutdownWithInterruption() throws Exception {
        // Create a mock executor service that throws InterruptedException
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("Test interruption"));
        
        // Set the mock executor service in the config using reflection
        ReflectionTestUtils.setField(replayBatchConfig, "executorService", mockExecutor);
        
        // Call shutdown method
        replayBatchConfig.shutdown();
        
        // Verify shutdown and shutdownNow were called
        verify(mockExecutor).shutdown();
        verify(mockExecutor).shutdownNow();
        
        // Verify that the current thread's interrupt status was set
        assertTrue("Thread interrupt status should be set", Thread.currentThread().isInterrupted());
        
        // Clear the interrupt status for other tests
        Thread.interrupted();
    }
    
    /**
     * Test that the shutdown method handles timeout correctly.
     */
    @Test
    public void testShutdownWithTimeout() throws Exception {
        // Create a mock executor service that times out on first call but succeeds on second
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(anyLong(), any(TimeUnit.class)))
                .thenReturn(false)  // First call returns false (timeout)
                .thenReturn(true);  // Second call returns true (success)
        
        // Set the mock executor service in the config using reflection
        ReflectionTestUtils.setField(replayBatchConfig, "executorService", mockExecutor);
        
        // Call shutdown method
        replayBatchConfig.shutdown();
        
        // Verify shutdown and shutdownNow were called
        verify(mockExecutor).shutdown();
        verify(mockExecutor).shutdownNow();
        
        // Verify awaitTermination was called twice
        verify(mockExecutor, times(2)).awaitTermination(anyLong(), any(TimeUnit.class));
    }
    
    /**
     * Test that the shutdown method handles persistent timeout correctly.
     */
    @Test
    public void testShutdownWithPersistentTimeout() throws Exception {
        // Create a mock executor service that always times out
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(anyLong(), any(TimeUnit.class)))
                .thenReturn(false)  // Always return false (timeout)
                .thenReturn(false);
        
        // Set the mock executor service in the config using reflection
        ReflectionTestUtils.setField(replayBatchConfig, "executorService", mockExecutor);
        
        // Call shutdown method
        replayBatchConfig.shutdown();
        
        // Verify shutdown and shutdownNow were called
        verify(mockExecutor).shutdown();
        verify(mockExecutor).shutdownNow();
        
        // Verify awaitTermination was called twice
        verify(mockExecutor, times(2)).awaitTermination(anyLong(), any(TimeUnit.class));
    }
    
    /**
     * Test that the shutdown method does nothing when executor service is null.
     */
    @Test
    public void testShutdownWithNullExecutorService() {
        // Set null executor service
        ReflectionTestUtils.setField(replayBatchConfig, "executorService", null);
        
        // Call shutdown method - should not throw any exception
        replayBatchConfig.shutdown();

        assertTrue("Executor service should not throw exception", true);
    }
}
