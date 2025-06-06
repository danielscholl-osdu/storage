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

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for replay batch processing.
 */
@Configuration
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayBatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayBatchConfig.class);

    /**
     *  Gets the configured batch size for replay operations.
     */
    @Getter
    @Value("${replay.batch.size:50}")
    private int batchSize;

    /**
     *  Gets the configured parallelism level for replay operations.
     */
    @Getter
    @Value("${replay.batch.parallelism:4}")
    private int parallelism;
    
    private ExecutorService executorService;
    
    /**
     * Creates an executor service for processing replay operations.
     * 
     * @return ExecutorService configured with the specified parallelism
     */
    @Bean(name = "replayExecutorService")
    public ExecutorService replayExecutorService() {
        LOGGER.info("Creating replay executor service with {} threads", parallelism);
        executorService = Executors.newFixedThreadPool(parallelism);
        return executorService;
    }
    
    /**
     * Properly shuts down the executor service when the application context is destroyed.
     */
    @PreDestroy
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                        LOGGER.error("ExecutorService did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
                LOGGER.error("Shutdown interrupted", e);
            }
        }
    }

}
