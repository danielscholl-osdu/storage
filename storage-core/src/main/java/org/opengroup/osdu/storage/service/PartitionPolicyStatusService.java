package org.opengroup.osdu.storage.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.storage.cache.PolicyCache;
import org.opengroup.osdu.storage.model.policy.PolicyStatus;
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
