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


import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.storage.provider.azure.interfaces.ISubscriptionRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@EnableScheduling
public abstract class AbstractSubscriptionScheduler {

    protected abstract ISubscriptionRegistry getSubscriptionRegistry();
    protected abstract String getTaskExecutorBeanName();

    // Default values - can be overridden by properties
    @Value("${subscription.scheduler.refreshInterval:300000}") // Default 5 minutes
    protected long refreshIntervalMs;

    @Value("${subscription.scheduler.initialDelay:300000}") // Default 5 minutes
    protected long initialDelayMs;

    // Base thread pool configuration - can be overridden by subclasses
    @Value("${subscription.scheduler.base.threadPool.coreSize:1}")
    protected int baseCorePoolSize;

    @Value("${subscription.scheduler.base.threadPool.maxSize:2}")
    protected int baseMaxPoolSize;

    @Value("${subscription.scheduler.base.threadPool.queueCapacity:10}")
    protected int baseQueueCapacity;

    @Value("${subscription.scheduler.base.threadPool.awaitTerminationSeconds:30}")
    protected int baseAwaitTerminationSeconds;

    // Tenant change tracking - using thread-safe concurrent set
    protected volatile Set<String> currentTenantSet = ConcurrentHashMap.newKeySet();
    protected final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    protected final AtomicBoolean initialSetupCompleted = new AtomicBoolean(false);
    protected final AtomicLong lastRefreshTime = new AtomicLong(0);
    protected final AtomicLong refreshCount = new AtomicLong(0);

    @PostConstruct
    public void initialize() {
        ISubscriptionRegistry registry = getSubscriptionRegistry();
        String subscriptionType = registry.getSubscriptionType();

        log.info("Initializing {}SubscriptionScheduler", subscriptionType);
        log.info("Refresh interval: {}ms, Initial delay: {}ms", refreshIntervalMs, initialDelayMs);

        if (!registry.isSchedulerEnabled()) {
            log.info("{}SubscriptionScheduler is disabled", subscriptionType);
        }
    }

    /**
     * Scheduled method that runs periodically to check for tenant changes
     */
    @Scheduled(fixedDelayString = "${subscription.scheduler.refreshInterval:300000}",
            initialDelayString = "${subscription.scheduler.initialDelay:30000}")
    public void scheduledTenantRefresh() {
        ISubscriptionRegistry registry = getSubscriptionRegistry();
        String subscriptionType = registry.getSubscriptionType();

        if (!registry.isSchedulerEnabled()) {
            return;
        }

        if (!refreshInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            long refreshNumber = refreshCount.incrementAndGet();

            // Get current tenant list
            Set<String> latestTenantSet = getCurrentTenantSet();

            if (latestTenantSet == null || latestTenantSet.isEmpty()) {
                log.warn("{} - No tenants available, skipping refresh #{}", subscriptionType, refreshNumber);
                return;
            }

            // Check for changes or initial setup
            if (hasTenantsChanged(latestTenantSet) || !initialSetupCompleted.get()) {
                log.info("{} - Tenant changes detected (refresh #{}). Tenants: {}",
                        subscriptionType, refreshNumber, latestTenantSet.size());

                updateTenantState(latestTenantSet);
                refreshSubscriptions();

                if (!initialSetupCompleted.get()) {
                    initialSetupCompleted.set(true);
                    log.info("{} - Initial subscription setup completed", subscriptionType);
                }
            }

        } catch (Exception e) {
            log.error("{} - Scheduled refresh failed: {}", subscriptionType, e.getMessage());
        }
        finally {
            refreshInProgress.set(false);
        }
    }

    /**
     * Check if tenants have changed
     */
    protected boolean hasTenantsChanged(Set<String> latestTenantSet) {
        if (currentTenantSet.size() != latestTenantSet.size()) {
            return true;
        }
        return !currentTenantSet.equals(latestTenantSet);
    }

    /**
     * Update tenant state
     */
    protected void updateTenantState(Set<String> latestTenantSet) {
        if (!currentTenantSet.equals(latestTenantSet)) {
            logTenantChanges(currentTenantSet, latestTenantSet, getSubscriptionRegistry().getSubscriptionType());
            // Replace the entire set reference atomically
            currentTenantSet = ConcurrentHashMap.newKeySet();
            currentTenantSet.addAll(latestTenantSet);
            lastRefreshTime.set(System.currentTimeMillis());
        }
    }

    /**
     * Manually trigger a subscription refresh
     */
    public void triggerRefresh() {
        String subscriptionType = getSubscriptionRegistry().getSubscriptionType();
        log.info("Manual {} subscription refresh triggered", subscriptionType);

        if (refreshInProgress.get()) {
            log.warn("{} refresh already in progress, manual trigger ignored", subscriptionType);
            return;
        }

        // Reset tenant set to force refresh - thread-safe
        currentTenantSet = ConcurrentHashMap.newKeySet();
        scheduledTenantRefresh();
    }

    protected Set<String> getCurrentTenantSet() {
        try {
            List<String> tenantList = getSubscriptionRegistry().getCurrentTenantList();
            return new HashSet<>(tenantList);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("ThreadScope")) {
                log.warn("ThreadScope not available in current context, returning empty tenant list");
                return new HashSet<>();
            }
            log.error("Error retrieving current tenant set for {}: {}",
                    getSubscriptionRegistry().getSubscriptionType(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Error retrieving current tenant set for {}: {}",
                    getSubscriptionRegistry().getSubscriptionType(), e.getMessage());
            return null;
        }
    }

    protected void refreshSubscriptions() {
        String subscriptionType = getSubscriptionRegistry().getSubscriptionType();
        try {
            log.info("Triggering {} subscription refresh...", subscriptionType);

            // Simply refresh all subscriptions
            getSubscriptionRegistry().subscribeToEvents();

            log.info("{} subscription refresh completed successfully", subscriptionType);

        } catch (Exception e) {
            log.error("Error during {} subscription refresh: {}", subscriptionType, e.getMessage());
        }
    }

    protected void logTenantChanges(Set<String> previousTenants, Set<String> currentTenants, String subscriptionType) {
        Set<String> addedTenants = new HashSet<>(currentTenants);
        addedTenants.removeAll(previousTenants);

        Set<String> removedTenants = new HashSet<>(previousTenants);
        removedTenants.removeAll(currentTenants);

        if (!addedTenants.isEmpty()) {
            log.info("New {} tenants detected: {}", subscriptionType, addedTenants);
        }

        if (!removedTenants.isEmpty()) {
            log.info("Removed {} tenants detected: {}", subscriptionType, removedTenants);
        }
    }

    /**
     * Create simple task executor for async operations
     */
    protected ThreadPoolTaskExecutor createTaskExecutor(String threadPrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(baseCorePoolSize);
        executor.setMaxPoolSize(baseMaxPoolSize);
        executor.setQueueCapacity(baseQueueCapacity);
        executor.setThreadNamePrefix(threadPrefix + "Scheduler-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(baseAwaitTerminationSeconds);
        
        log.info("Created configurable {} scheduler executor (core={}, max={}, queue={}, timeout={})", 
                threadPrefix, baseCorePoolSize, baseMaxPoolSize, baseQueueCapacity, baseAwaitTerminationSeconds);
        return executor;
    }

    @PreDestroy
    public void cleanup() {
        String subscriptionType = getSubscriptionRegistry().getSubscriptionType();
        log.info("Shutting down {}SubscriptionScheduler...", subscriptionType);
        refreshInProgress.set(false);
        log.info("{}SubscriptionScheduler shutdown completed", subscriptionType);
    }
}
