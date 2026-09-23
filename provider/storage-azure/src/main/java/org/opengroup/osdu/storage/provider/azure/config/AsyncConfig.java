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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${async.executor.threadPool.coreSize:3}")
    private int corePoolSize;
    
    @Value("${async.executor.threadPool.maxSize:6}")
    private int maxPoolSize;
    
    @Value("${async.executor.threadPool.queueCapacity:100}")
    private int queueCapacity;
    
    @Value("${async.executor.threadPool.threadNamePrefix:Primary-Async-}")
    private String threadNamePrefix;
    
    @Value("${async.executor.threadPool.waitForTasksToCompleteOnShutdown:true}")
    private boolean waitForTasksToCompleteOnShutdown;
    
    @Value("${async.executor.threadPool.awaitTerminationSeconds:60}")
    private int awaitTerminationSeconds;

    /**
     * Primary task executor for general async processing
     * This executor creates separate threads and does NOT run on main thread
     */
    @Bean(name = "taskExecutor")
    @Primary
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        
        // Add thread verification logging
        executor.setTaskDecorator(runnable -> {
            return () -> {
                System.out.println("[ASYNC-VERIFY] Task executing on thread: " + 
                                 Thread.currentThread().getName() + " (NOT main thread)");
                runnable.run();
            };
        });
        
        // Must initialize to create the thread pool
        executor.initialize();
        
        System.out.println("[ASYNC-CONFIG] Primary TaskExecutor initialized with " + 
                          executor.getCorePoolSize() + " core threads");
        return executor;
    }
}
