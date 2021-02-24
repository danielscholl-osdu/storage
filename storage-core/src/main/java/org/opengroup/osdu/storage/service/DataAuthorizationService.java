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

package org.opengroup.osdu.storage.service;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.policy.PolicyRequest;
import org.opengroup.osdu.core.common.model.policy.PolicyResponse;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.policy.di.PolicyServiceConfiguration;
import org.opengroup.osdu.storage.policy.model.StoragePolicy;

import org.opengroup.osdu.storage.policy.service.IPolicyService;
import org.opengroup.osdu.storage.policy.service.PartitionPolicyStatusService;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.util.PatchOperationApplicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class DataAuthorizationService {

    @Lazy
    @Autowired
    private PolicyServiceConfiguration policyServiceConfiguration;

    @Autowired
    private DpsHeaders headers;

    @Autowired(required = false)
    private IPolicyService policyService;

    @Autowired
    private PartitionPolicyStatusService statusService;

    @Autowired
    private IEntitlementsExtensionService entitlementsService;

    @Lazy
    @Autowired
    private ICloudStorage cloudStorage;

    private PatchOperationApplicator patchOperationApplicator = new PatchOperationApplicator();

    public boolean validateOwnerAccess(RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        return this.entitlementsService.hasOwnerAccess(this.headers, recordMetadata.getAcl().getOwners());
    }

    public boolean validateViewerOrOwnerAccess(RecordMetadata recordMetadata, OperationType operationType) {
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

    public boolean validateViewerAccess(
            BiFunction<DpsHeaders, Set<String>, Boolean> entitlementHasAccess, RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        return entitlementHasAccess.apply(this.headers, new HashSet<>(Arrays.asList(recordMetadata.getAcl().getViewers())));
    }

    public boolean validateOwnerAccess(
            BiFunction<DpsHeaders, Set<String>, Boolean> entitlementHasAccess, RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        return entitlementHasAccess.apply(this.headers, new HashSet<>(Arrays.asList(recordMetadata.getAcl().getOwners())));
    }

    public void validateUserAccessAndComplianceConstraints(
            BiConsumer<List<Record>, Map<String, RecordMetadata>> storageValidator, List<Record> inputRecords, Map<String, RecordMetadata> existingRecords) {
        if (this.policyEnabled()) {
            evaluateStorageDataAuthorizationPolicy(inputRecords, existingRecords);
            return;
        }

        storageValidator.accept(inputRecords, existingRecords);
    }

    public List<String> validateUserAccessAndComplianceConstraints(
            Consumer<List<PatchOperation>> complianceValidator, BiFunction<Map<String, String>, Map<String, RecordMetadata>, List<String>> accessValidator,
            List<PatchOperation> bulkUpdateOps, Map<String, String> idMap, Map<String, RecordMetadata> existingRecords) {
        if (this.policyEnabled()) {
            return evaluateBulkUpdateStorageDataAuthorizationPolicy(bulkUpdateOps, idMap, existingRecords);
        }

        complianceValidator.accept(bulkUpdateOps);
        return accessValidator.apply(idMap, existingRecords);
    }

    private void evaluateStorageDataAuthorizationPolicy(List<Record> inputRecords, Map<String, RecordMetadata> existingRecords) {
        for (Record record : inputRecords) {
            RecordMetadata recordMetadata;
            OperationType operationType;
            if (existingRecords.containsKey(record.getId())) {
                recordMetadata = existingRecords.get(record.getId());
                operationType = OperationType.update;
            } else {
                recordMetadata = new RecordMetadata(record);
                operationType = OperationType.create;
            }
            if (!evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType)) {
                throw new AppException(HttpStatus.SC_FORBIDDEN,
                        "User Unauthorized", "User is not authorized to create or update records.", String.format("User does not have required access to record %s", record.getId()));
            }
        }
    }

    private List<String> evaluateBulkUpdateStorageDataAuthorizationPolicy(List<PatchOperation> bulkUpdateOps, Map<String, String> idMap, Map<String, RecordMetadata> existingRecords) {
        List<String> unauthorizedRecordIds = new ArrayList<>();
        for (String id : idMap.keySet()) {
            String idWithVersion = idMap.get(id);
            RecordMetadata metadata = existingRecords.get(id);

            if (metadata == null) continue;

            metadata = this.patchOperationApplicator.updateMetadataWithOperations(metadata, bulkUpdateOps);

            if (!evaluateStorageDataAuthorizationPolicy(metadata, OperationType.update)) {
                unauthorizedRecordIds.add(idWithVersion);
            }
        }
        return unauthorizedRecordIds;
    }

    public boolean policyEnabled() {
        return this.policyService != null && this.statusService.policyEnabled(this.headers.getPartitionId());
    }

    private boolean evaluateStorageDataAuthorizationPolicy(RecordMetadata recordMetadata, OperationType operationType) {
        PolicyResponse policyResponse = policyService.evaluatePolicy(this.getStoragePolicy(recordMetadata, operationType));
        return policyResponse.getResult().isAllow();
    }

    private PolicyRequest getStoragePolicy(RecordMetadata recordMetadata, OperationType operation) {
        Record record = new Record();
        record.setId(recordMetadata.getId());
        record.setKind(recordMetadata.getKind());
        record.setAcl(recordMetadata.getAcl());
        record.setLegal(recordMetadata.getLegal());

        StoragePolicy storagePolicy = new StoragePolicy();
        storagePolicy.setOperation(operation);
        storagePolicy.setGroups(this.getGroups());
        storagePolicy.setRecord(record);

        PolicyRequest policy = new PolicyRequest();
        policy.setPolicyId(policyServiceConfiguration.getPolicyId());
        policy.setInput(new JsonParser().parse(new Gson().toJson(storagePolicy)).getAsJsonObject());

        return policy;
    }

    private List<String> getGroups() {
        return this.entitlementsService.getGroups(this.headers)
                .getGroups().stream().map(GroupInfo::getEmail).distinct().collect(Collectors.toList());
    }
}
