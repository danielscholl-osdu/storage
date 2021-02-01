package org.opengroup.osdu.storage.model.policy;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.storage.service.IPartitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Component
public class StoragePolicy {

    @Value("${POLICY_ID}")
    private String policyId;

    private PolicyInput input;

    @Autowired
    private IEntitlementsFactory entitlementsFactory;

    @Autowired
    private IPartitionService partitionService;

    @Autowired
    private DpsHeaders headers;

    public StoragePolicy getStoragePolicy(RecordMetadata recordMetadata, OperationType operation) {
        PolicyInput policyInput = new PolicyInput();
        policyInput.setOperation(operation);
        StoragePolicy policy = new StoragePolicy();

        policy.setPolicyId(policyId);

        List<String> groups = new ArrayList<>();
        IEntitlementsService service = this.entitlementsFactory.create(headers);
        Groups entGroups;
        try {
            entGroups = service.getGroups();
        } catch (EntitlementsException e) {
            throw new AppException(e.getHttpResponse().getResponseCode(), "Access Denied", "The user is not authorized to perform this action", e);
        }
        for(GroupInfo groupInfo : entGroups.getGroups()) {
            groups.add(groupInfo.getEmail());
        }
        policyInput.setGroups(groups);
        policyInput.setLegalTags(recordMetadata.getLegal().getLegaltags());

        Record record = new Record();
        record.setId(recordMetadata.getId());
        record.setKind(recordMetadata.getKind());
        record.setAcl(recordMetadata.getAcl());
        record.setLegal(recordMetadata.getLegal());

        policyInput.setRecord(record);
        policy.setInput(policyInput);

        return policy;
    }

    public boolean authWithEntitlements() {
        String partitionPolicyProperty = "";
        PartitionInfo partitionInfo = null;
        try {
            partitionInfo = partitionService.getPartition(headers.getPartitionId());
        } catch (AppException e) {
            switch (e.getError().getCode()) {
                case HttpStatus.SC_NOT_IMPLEMENTED: return true;
                case HttpStatus.SC_INTERNAL_SERVER_ERROR: return true;
            }
        }
        final Gson gson = new Gson();

        JsonElement element = gson.toJsonTree(partitionInfo.getProperties());
        JsonObject rootObject = element.getAsJsonObject();
        if(rootObject.has("policy-service-enabled")) {
            partitionPolicyProperty = rootObject.getAsJsonObject("policy-service-enabled").get("value").getAsString();
            if(partitionPolicyProperty.equalsIgnoreCase("true")) {
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }
}
