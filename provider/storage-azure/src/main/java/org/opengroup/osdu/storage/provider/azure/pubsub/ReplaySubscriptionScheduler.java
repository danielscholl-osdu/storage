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

import org.opengroup.osdu.storage.provider.azure.interfaces.ISubscriptionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnProperty(value = {"feature.replay.enabled", "replay.subscription.scheduler.enabled"}, 
                      havingValue = "true", matchIfMissing = false)
@Component
@Slf4j
public class ReplaySubscriptionScheduler extends AbstractSubscriptionScheduler {

    @Autowired
    private ReplaySubscriptionRegistry replaySubscriptionRegistry;

    @Value("${replay.scheduler.threadPool.coreSize:1}")
    private int corePoolSize;

    @Value("${replay.scheduler.threadPool.maxSize:2}")
    private int maxPoolSize;

    @Value("${replay.scheduler.threadPool.queueCapacity:10}")
    private int queueCapacity;

    @Value("${replay.scheduler.threadPool.awaitTerminationSeconds:30}")
    private int awaitTerminationSeconds;

    @Value("${replay.scheduler.threadPool.keepAliveSeconds:60}")
    private int keepAliveSeconds;

    @Value("${replay.scheduler.threadPool.allowCoreThreadTimeout:true}")
    private boolean allowCoreThreadTimeOut;

    @Override
    protected ISubscriptionRegistry getSubscriptionRegistry() {
        log.debug("Getting replay subscription registry for scheduled check");
        return replaySubscriptionRegistry;
    }

    @Override
    protected String getTaskExecutorBeanName() {
        return "replaySchedulerTaskExecutor";
    }

    /**
     * Optimized task executor for replay subscription scheduling
     * Uses minimal thread pool since this is periodic scheduling, not high-throughput processing
     */
    @Bean("replaySchedulerTaskExecutor")
    public ThreadPoolTaskExecutor replaySchedulerTaskExecutor() {
        ThreadPoolTaskExecutor executor = createOptimizedTaskExecutor("Replay");
        executor.setBeanName("replaySchedulerTaskExecutor");
        executor.initialize();
        
        log.info("Replay scheduler task executor initialized - this will run periodic subscription checks");
        return executor;
    }
    
    /**
     * Create optimized executor for scheduling tasks
     */
    private ThreadPoolTaskExecutor createOptimizedTaskExecutor(String threadPrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadPrefix + "Sched-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.setAllowCoreThreadTimeOut(allowCoreThreadTimeOut);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        
        log.info("Created configurable {} scheduler executor (core={}, max={}, queue={}, timeout={})", 
                threadPrefix, corePoolSize, maxPoolSize, queueCapacity, awaitTerminationSeconds);
        return executor;
    }
}
