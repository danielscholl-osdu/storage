// Copyright © Schlumberger
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

package org.opengroup.osdu.storage.policy.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.policy.PolicyStatus;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.storage.policy.cache.PolicyCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class PartitionPolicyStatusService {

    private final static String POLICY_SERVICE_ENABLED = "policy-service-enabled";

    @Autowired(required = false)
    private IPartitionService partitionService;

    @Lazy
    @Autowired
    private PolicyCache cache;

    @Autowired
    private JaxRsDpsLog logger;

    public boolean policyEnabled(String dataPartitionId) {
        if (partitionService == null) return false;

        String cacheKey = String.format("%s-policy", dataPartitionId);

        if (cache != null && cache.containsKey(cacheKey)) return cache.get(cacheKey).isEnabled();

        PolicyStatus policyStatus = PolicyStatus.builder().enabled(false).build();

        try {
            PartitionInfo partitionInfo = partitionService.getPartition(dataPartitionId);
            policyStatus.setEnabled(getPolicyStatus(partitionInfo));
        } catch (Exception e) {
            this.logger.error(String.format("Error getting policy status for dataPartitionId: %s", dataPartitionId), e);
        }

        this.cache.put(cacheKey, policyStatus);

        return policyStatus.isEnabled();
    }

    private boolean getPolicyStatus(PartitionInfo partitionInfo) {
        final Gson gson = new Gson();
        JsonElement element = gson.toJsonTree(partitionInfo.getProperties());
        JsonObject rootObject = element.getAsJsonObject();
        if (!rootObject.has(POLICY_SERVICE_ENABLED)) {
            return false;
        }

        String partitionPolicyProperty = rootObject.getAsJsonObject(POLICY_SERVICE_ENABLED).get("value").getAsString();
        return partitionPolicyProperty.equalsIgnoreCase("true");
    }
}
