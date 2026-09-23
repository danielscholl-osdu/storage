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

import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.MessageHandlerOptions;
import com.microsoft.azure.servicebus.SubscriptionClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.opengroup.osdu.azure.servicebus.ISubscriptionClientFactory;
import org.opengroup.osdu.storage.provider.azure.di.ServiceBusConfig;
import org.opengroup.osdu.storage.provider.azure.interfaces.ISubscriptionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SubscriptionManager {

    private final Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    @Autowired
    protected ServiceBusConfig serviceBusConfig;

    @Autowired
    protected ISubscriptionClientFactory subscriptionClientFactory;

    @Autowired(required = false)
    protected LegalComplianceChangeUpdate legalComplianceChangeUpdate;

    @Value("${subscription.manager.cleanup.awaitTerminationSeconds:10}")
    private int cleanupAwaitTerminationSeconds;

    @Value("${subscription.manager.messageHandler.maxConcurrentCalls:1}")
    private int maxConcurrentCalls;

    @Value("${subscription.manager.messageHandler.autoComplete:false}")
    private boolean autoComplete;

    @Value("${subscription.manager.messageHandler.maxAutoRenewDurationMinutes:5}")
    private int maxAutoRenewDurationMinutes;

    // Resource tracking per registry type
    private final Map<String, Map<String, SubscriptionClient>> activeSubscriptionClients = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> executorServices = new ConcurrentHashMap<>();

    // State tracking per registry type
    private final Map<String, AtomicBoolean> initializationStatus = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastSubscriptionTimes = new ConcurrentHashMap<>();

    // Add tracking for active message processing per tenant
    private final Map<String, Set<String>> activeMessageProcessing = new ConcurrentHashMap<>();

    /**
     * Subscribe to events for a specific registry - now supports incremental updates
     */
    public synchronized void subscribeToEvents(ISubscriptionRegistry registry) {
        String subscriptionType = registry.getSubscriptionType();
        logger.info("Starting subscription to {} events", subscriptionType);

        try {
            // Validate dependencies
            if (!validateDependencies(registry)) {
                logger.warn("Dependencies validation failed, cannot setup {} subscriptions", subscriptionType);
                return;
            }

            // Get current tenant list from registry
            List<String> tenantList = registry.getCurrentTenantList();

            if (tenantList.isEmpty()) {
                logger.warn("No valid tenants found, skipping {} subscription setup", subscriptionType);
                return;
            }

            logger.info("Setting up {} subscriptions for {} tenants", subscriptionType, tenantList.size());

            // Get current active subscriptions
            Set<String> currentTenants = getActiveSubscriptions(subscriptionType);
            Set<String> newTenants = new HashSet<>(tenantList);
            
            // Calculate incremental changes
            Set<String> tenantsToAdd = new HashSet<>(newTenants);
            tenantsToAdd.removeAll(currentTenants);
            
            Set<String> tenantsToRemove = new HashSet<>(currentTenants);
            tenantsToRemove.removeAll(newTenants);
            
            // Tenants that exist in both sets - keep these connections alive
            Set<String> tenantsToKeep = new HashSet<>(currentTenants);
            tenantsToKeep.retainAll(newTenants);

            logger.info("{} subscription changes - Add: {}, Remove: {}, Keep: {}", 
                       subscriptionType, tenantsToAdd.size(), tenantsToRemove.size(), tenantsToKeep.size());

            // Only proceed if there are actual changes
            if (tenantsToAdd.isEmpty() && tenantsToRemove.isEmpty()) {
                logger.debug("No changes needed for {} subscriptions", subscriptionType);
                return;
            }

            // Initialize resources if this is the first setup
            if (currentTenants.isEmpty()) {
                ExecutorService executorService = Executors.newFixedThreadPool(Integer.parseUnsignedInt(serviceBusConfig.getSbExecutorThreadPoolSize()));
                executorServices.put(subscriptionType, executorService);
                activeSubscriptionClients.putIfAbsent(subscriptionType, new ConcurrentHashMap<>());
            }

            ExecutorService executorService = executorServices.get(subscriptionType);
            Map<String, SubscriptionClient> clientMap = activeSubscriptionClients.get(subscriptionType);

            int successCount = 0;
            int failureCount = 0;

            // Remove tenants that are no longer needed (only those NOT in new tenant set)
            for (String tenantToRemove : tenantsToRemove) {
                try {
                    removeSubscriptionForTenant(tenantToRemove, subscriptionType, clientMap);
                    logger.info("Removed {} subscription for tenant: {}", subscriptionType, tenantToRemove);
                } catch (Exception e) {
                    logger.warn("Failed to remove {} subscription for tenant {}: {}", subscriptionType, tenantToRemove, e.getMessage(), e);
                }
            }

            // Add new tenants (only those that weren't already active)
            for (String tenantToAdd : tenantsToAdd) {
                try {
                    if (setupSubscriptionForTenant(tenantToAdd, registry, executorService, clientMap)) {
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to setup {} subscription for partition {}: {}", subscriptionType, tenantToAdd, e.getMessage(), e);
                    failureCount++;
                }
            }

            // Log which tenants are being kept
            if (!tenantsToKeep.isEmpty()) {
                logger.debug("Keeping existing {} subscriptions for tenants: {}", subscriptionType, tenantsToKeep);
            }

            logger.info("{} incremental subscription update completed - Success: {}, Failures: {}", subscriptionType, successCount, failureCount);

            if (successCount > 0 || !currentTenants.isEmpty()) {
                initializationStatus.computeIfAbsent(subscriptionType, k -> new AtomicBoolean(true));
                lastSubscriptionTimes.computeIfAbsent(subscriptionType, k -> new AtomicLong(0)).set(System.currentTimeMillis());
            }

        } catch (Exception e) {
            logger.warn("Error during subscription to {} events: {}", subscriptionType, e.getMessage(), e);
            return;
        }
    }

    /**
     * Gracefully remove subscription for a single tenant
     */
    private void removeSubscriptionForTenant(String tenant, String subscriptionType, Map<String, SubscriptionClient> clientMap) {
        SubscriptionClient client = clientMap.remove(tenant);
        if (client != null) {
            try {
                client.close();
                logger.debug("Closed {} subscription client for tenant: {}", subscriptionType, tenant);
            } catch (Exception e) {
                logger.warn("Error closing {} subscription client for tenant {}: {}", subscriptionType, tenant, e.getMessage());
            }
        }
    }

    /**
     * Mark tenant as actively processing (to be called by message handlers)
     */
    public void markTenantProcessingStart(String subscriptionType, String tenant) {
        activeMessageProcessing.computeIfAbsent(subscriptionType, k -> ConcurrentHashMap.newKeySet()).add(tenant);
    }

    /**
     * Mark tenant as finished processing (to be called by message handlers)
     */
    public void markTenantProcessingEnd(String subscriptionType, String tenant) {
        Set<String> processingTenants = activeMessageProcessing.get(subscriptionType);
        if (processingTenants != null) {
            processingTenants.remove(tenant);
        }
    }

    private boolean setupSubscriptionForTenant(String partition, ISubscriptionRegistry registry, ExecutorService executorService, Map<String, SubscriptionClient> clientMap) {
        try {
            String subscriptionType = registry.getSubscriptionType();
            logger.info("Setting up {} subscription for partition: {}", subscriptionType, partition);

            // Use direct topic/subscription access instead of SubscriptionConfig
            String topic = registry.getTopic();
            String subscription = registry.getSubscription();

            logger.debug("Using topic: {}, subscription: {}", topic, subscription);

            SubscriptionClient subscriptionClient = subscriptionClientFactory.getClient(partition, topic, subscription);

            if (subscriptionClient != null) {
                logger.debug("SubscriptionClient created for partition: {}", partition);

                registerMessageHandler(subscriptionClient, registry.getMessageHandler(), executorService);
                clientMap.put(partition, subscriptionClient);

                logger.info("Successfully subscribed to {} events for partition: {}", subscriptionType, partition);
                return true;
            }
            else {
                logger.warn("Failed to create SubscriptionClient for partition: {}", partition);
                return false;
            }

        } catch (Exception e) {
            logger.warn("Error setting up {} subscription for partition {}: {}", registry.getSubscriptionType(), partition, e.getMessage(), e);
            return false;
        }
    }

    private void registerMessageHandler(SubscriptionClient subscriptionClient, Object messageHandler, ExecutorService executorService) throws ServiceBusException, InterruptedException {

        MessageHandlerOptions options = new MessageHandlerOptions(maxConcurrentCalls, autoComplete, Duration.ofMinutes(maxAutoRenewDurationMinutes));

        if (messageHandler instanceof ReplayMessageHandler) {
            ReplaySubscriptionMessageHandler wrapper = new ReplaySubscriptionMessageHandler(subscriptionClient, (ReplayMessageHandler) messageHandler);
            subscriptionClient.registerMessageHandler(wrapper, options, executorService);
        }
        else if (messageHandler instanceof LegalTagMessageHandler) {
            LegalTagSubscriptionMessageHandler wrapper = new LegalTagSubscriptionMessageHandler(subscriptionClient, legalComplianceChangeUpdate);
            subscriptionClient.registerMessageHandler(wrapper, options, executorService);
        }
        else {
            throw new IllegalArgumentException("Unsupported message handler type: " + messageHandler.getClass().getSimpleName());
        }
    }

    private boolean validateDependencies(ISubscriptionRegistry registry) {
        if (serviceBusConfig == null || subscriptionClientFactory == null) {
            logger.warn("Required dependencies are null");
            return false;
        }

        if (registry.getMessageHandler() == null) {
            logger.warn("{} MessageHandler is null", registry.getSubscriptionType());
            return false;
        }

        String topic = registry.getTopic();
        String subscription = registry.getSubscription();
        if (topic == null || topic.trim().isEmpty() || subscription == null || subscription.trim().isEmpty()) {
            logger.warn("{} subscription configuration is incomplete - topic: {}, subscription: {}", registry.getSubscriptionType(), topic, subscription);
            return false;
        }

        return true;
    }

    private void cleanupExistingSubscriptions(String subscriptionType) {
        Map<String, SubscriptionClient> clientMap = activeSubscriptionClients.get(subscriptionType);
        if (clientMap != null && !clientMap.isEmpty()) {
            logger.info("Cleaning up {} existing {} subscription clients", clientMap.size(), subscriptionType);

            for (Map.Entry<String, SubscriptionClient> entry : clientMap.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (Exception e) {
                    logger.warn("Error closing {} subscription client for partition {}: {}", subscriptionType, entry.getKey(), e.getMessage());
                }
            }
            clientMap.clear();
        }

        ExecutorService executorService = executorServices.get(subscriptionType);
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(cleanupAwaitTerminationSeconds, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
            executorServices.remove(subscriptionType);
        }

        AtomicBoolean initialized = initializationStatus.get(subscriptionType);
        if (initialized != null) {
            initialized.set(false);
        }
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down SubscriptionManager...");
        // Close all active subscriptions
        for (Map<String, SubscriptionClient> clientMap : activeSubscriptionClients.values()) {
            for (SubscriptionClient client : clientMap.values()) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Error closing subscription client: {}", e.getMessage());
                }
            }
        }
        activeSubscriptionClients.clear();

        for (ExecutorService executorService : executorServices.values()) {
            try {
            // Shutdown all executor services
            executorService.shutdownNow();
            } catch (Exception e) {
            logger.warn("Error shutting down executor service: {}", e.getMessage());
            }
        }
        executorServices.clear();

        logger.info("SubscriptionManager shutdown completed");
    }

    /*
     * Get active subscriptions for a specific subscription type
     */
    public Set<String> getActiveSubscriptions(String subscriptionType) {
        Map<String, SubscriptionClient> clientMap = activeSubscriptionClients.get(subscriptionType);
        if (clientMap == null) {
            return Set.of();
        }
        return clientMap.keySet();
    }
}
