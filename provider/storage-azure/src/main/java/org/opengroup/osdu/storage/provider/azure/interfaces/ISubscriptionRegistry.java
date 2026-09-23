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

package org.opengroup.osdu.storage.provider.azure.interfaces;

import java.util.List;
import java.util.Set;

public interface ISubscriptionRegistry {
    
    /**
     * Subscribe to events for this subscription type
     */
    void subscribeToEvents();
    
    /**
     * Get current tenant list
     */
    List<String> getCurrentTenantList();
    
    /**
     * Get the subscription type name for logging
     */
    String getSubscriptionType();
    
    /**
     * Check if this subscription is enabled
     */
    boolean isEnabled();
    
    /**
     * Get the feature flag property name
     */
    String getFeatureFlagProperty();
    
    /**
     * Determines whether the scheduler is enabled for subscription type.
     */
    default boolean isSchedulerEnabled() {
        return isEnabled();
    }
    
    /**
     * Get scheduler configuration property prefix
     */
    default String getSchedulerPropertyPrefix() {
        return getSubscriptionType().toLowerCase() + ".subscription";
    }
    
    /**
     * Get topic name for this subscription type
     */
    String getTopic();
    
    /**
     * Get subscription name for this subscription type  
     */
    String getSubscription();
    
    /**
     * Get message handler for this subscription type
     */
    Object getMessageHandler();
    
    /**
     * Get currently active subscriptions for this registry type
     */
    Set<String> getActiveSubscriptions();
    
}