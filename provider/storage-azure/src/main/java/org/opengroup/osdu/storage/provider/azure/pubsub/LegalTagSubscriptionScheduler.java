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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@ConditionalOnProperty(value = {"azure.feature.legaltag-compliance-update.enabled", "legaltag.subscription.scheduler.enabled"}, 
                      havingValue = "true", matchIfMissing = false)
@Component
@Slf4j
public class LegalTagSubscriptionScheduler extends AbstractSubscriptionScheduler {

    @Autowired
    private LegalTagSubscriptionRegistry legalTagSubscriptionRegistry;

    @Value("${legalTag.scheduler.threadPool.coreSize:1}")
    private int corePoolSize;

    @Value("${legalTag.scheduler.threadPool.maxSize:2}")
    private int maxPoolSize;

    @Value("${legalTag.scheduler.threadPool.queueCapacity:10}")
    private int queueCapacity;

    @Value("${legalTag.scheduler.threadPool.awaitTerminationSeconds:30}")
    private int awaitTerminationSeconds;

    @Value("${legalTag.scheduler.threadPool.keepAliveSeconds:60}")
    private int keepAliveSeconds;

    @Value("${legalTag.scheduler.threadPool.allowCoreThreadTimeout:true}")
    private boolean allowCoreThreadTimeOut;

    @Override
    protected ISubscriptionRegistry getSubscriptionRegistry() {
        return legalTagSubscriptionRegistry;
    }

    @Override
    protected String getTaskExecutorBeanName() {
        return "legalTagSchedulerTaskExecutor";
    }

    /**
     * Optimized task executor for legal tag subscription scheduling
     * Uses minimal thread pool since this is periodic scheduling, not high-throughput processing
     */
    @Bean("legalTagSchedulerTaskExecutor")
    public ThreadPoolTaskExecutor legalTagSchedulerTaskExecutor() {
        ThreadPoolTaskExecutor executor = createOptimizedTaskExecutor("LegalTag");
        executor.setBeanName("legalTagSchedulerTaskExecutor");
        executor.initialize();
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
