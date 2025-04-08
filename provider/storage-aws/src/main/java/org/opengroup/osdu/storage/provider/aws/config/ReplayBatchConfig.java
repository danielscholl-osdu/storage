package org.opengroup.osdu.storage.provider.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Configuration for replay batch processing.
 */
@Configuration
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayBatchConfig {
    private static final Logger LOGGER = Logger.getLogger(ReplayBatchConfig.class.getName());

    @Value("${replay.batch.size:50}")
    private int batchSize;
    
    @Value("${replay.batch.parallelism:4}")
    private int parallelism;
    
    /**
     * Creates an executor service for processing replay operations.
     */
    @Bean(name = "replayExecutorService")
    public ExecutorService replayExecutorService() {
        LOGGER.info("Creating replay executor service with " + parallelism + " threads");
        return Executors.newFixedThreadPool(parallelism);
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public int getParallelism() {
        return parallelism;
    }
}
