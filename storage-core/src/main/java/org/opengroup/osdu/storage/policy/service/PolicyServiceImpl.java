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
import com.google.gson.JsonParser;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.policy.PolicyRequest;
import org.opengroup.osdu.core.common.model.policy.PolicyResponse;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.policy.IPolicyFactory;
import org.opengroup.osdu.core.common.policy.IPolicyProvider;
import org.opengroup.osdu.storage.policy.di.PolicyServiceConfiguration;
import org.opengroup.osdu.storage.policy.model.StoragePolicy;
import org.opengroup.osdu.storage.service.IEntitlementsExtensionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(value = "service.policy.enabled", havingValue = "true", matchIfMissing = false)
public class PolicyServiceImpl implements IPolicyService {

    @Autowired
    private PolicyServiceConfiguration policyServiceConfiguration;

    @Autowired
    private IPolicyFactory policyFactory;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private IEntitlementsExtensionService entitlementsService;

    @Override
    public PolicyResponse evaluatePolicy(PolicyRequest policy) {

        try {
            IPolicyProvider serviceClient = this.policyFactory.create(this.headers);
            return serviceClient.evaluatePolicy(policy);
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Policy service unavailable", "Error making request to Policy service", e);
        }
    }

    public boolean evaluateStorageDataAuthorizationPolicy(RecordMetadata recordMetadata, OperationType operationType) {
        PolicyResponse policyResponse = this.evaluatePolicy(this.getStoragePolicy(recordMetadata, operationType));
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
        policy.setPolicyId(this.policyServiceConfiguration.getPolicyId());
        policy.setInput(new JsonParser().parse(new Gson().toJson(storagePolicy)).getAsJsonObject());

        return policy;
    }

    private List<String> getGroups() {
        return this.entitlementsService.getGroups(this.headers)
                .getGroups().stream().map(GroupInfo::getEmail).distinct().collect(Collectors.toList());
    }
}

