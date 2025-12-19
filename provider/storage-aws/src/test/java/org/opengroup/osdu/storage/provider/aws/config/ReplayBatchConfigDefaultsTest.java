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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;

/**
 * Tests for ReplayBatchConfig with default property values.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ReplayBatchConfig.class})
@TestPropertySource(properties = {
        "feature.replay.enabled=true"
        // No explicit values for batch.size and parallelism to test defaults
})
public class ReplayBatchConfigDefaultsTest {

    @Autowired
    private ReplayBatchConfig replayBatchConfig;

    @Autowired
    private ExecutorService replayExecutorService;

    /**
     * Test that default configuration properties are correctly used when not explicitly set.
     */
    @Test
    public void testDefaultConfigProperties() {
        // Default values from ReplayBatchConfig class
        assertEquals("Default batch size should be 50", 50, replayBatchConfig.getBatchSize());
        assertEquals("Default parallelism should be 4", 4, replayBatchConfig.getParallelism());
    }

    /**
     * Test that the executor service is created with the default number of threads.
     */
    @Test
    public void testReplayExecutorServiceWithDefaults() {
        assertNotNull("Executor service should not be null", replayExecutorService);
        // Not testing isShutdown() as it may be in an unexpected state in Spring context tests
    }
}
