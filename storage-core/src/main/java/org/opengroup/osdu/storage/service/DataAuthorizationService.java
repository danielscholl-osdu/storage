package org.opengroup.osdu.storage.service;

import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.di.PolicyServiceConfiguration;
import org.opengroup.osdu.storage.model.policy.PolicyInput;
import org.opengroup.osdu.storage.model.policy.PolicyResponse;
import org.opengroup.osdu.storage.model.policy.StoragePolicy;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BiFunction;

@Service
public class DataAuthorizationService {

    @Autowired
    private PolicyServiceConfiguration policyServiceConfiguration;

    @Autowired
    private DpsHeaders headers;

    @Autowired(required = false)
    private IPolicyService policyService;

    @Autowired
    private PartitionPolicyStatusService statusService;

    @Autowired
    private IEntitlementsAndCacheService entitlementsService;

    @Lazy
    @Autowired
    private ICloudStorage cloudStorage;

    public boolean hasOwnerAccess(RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        return this.entitlementsService.hasOwnerAccess(this.headers, recordMetadata.getAcl().getOwners());
    }

    public boolean hasValidAccess(RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        List<RecordMetadata> postAclCheck = this.entitlementsService.hasValidAccess(Collections.singletonList(recordMetadata), this.headers);
        return postAclCheck != null && !postAclCheck.isEmpty();
    }

    public boolean hasAccess(RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        return this.cloudStorage.hasAccess(recordMetadata);
    }

    public boolean hasViewerAccess(BiFunction<DpsHeaders, Set<String>, Boolean> entitlementHasAccess, RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        return entitlementHasAccess.apply(this.headers, new HashSet<>(Arrays.asList(recordMetadata.getAcl().getViewers())));
    }

    public boolean hasOwnerAccess(BiFunction<DpsHeaders, Set<String>, Boolean> entitlementHasAccess, RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        return entitlementHasAccess.apply(this.headers, new HashSet<>(Arrays.asList(recordMetadata.getAcl().getOwners())));
    }

    private boolean policyEnabled() {
        return this.policyService != null && this.statusService.policyEnabled(this.headers.getPartitionId());
    }

    private boolean evaluateStorageDataAuthorizationPolicy(RecordMetadata recordMetadata, OperationType operationType) {
        PolicyResponse policyResponse = policyService.evaluatePolicy(getStoragePolicy(recordMetadata, operationType));
        return policyResponse.getResult().isAllow();
    }

    private StoragePolicy getStoragePolicy(RecordMetadata recordMetadata, OperationType operation) {
        Record record = new Record();
        record.setId(recordMetadata.getId());
        record.setKind(recordMetadata.getKind());
        record.setAcl(recordMetadata.getAcl());
        record.setLegal(recordMetadata.getLegal());

        PolicyInput policyInput = new PolicyInput();
        policyInput.setOperation(operation);
        policyInput.setGroups(this.headers.getHeaders().get("groups"));
        policyInput.setLegalTags(recordMetadata.getLegal().getLegaltags());
        policyInput.setOtherRelevantDataCountries(record.getLegal().getOtherRelevantDataCountries());
        policyInput.setRecord(record);

        StoragePolicy policy = new StoragePolicy();
        policy.setPolicyId(policyServiceConfiguration.getPolicyId());
        policy.setInput(policyInput);

        return policy;
    }
}
