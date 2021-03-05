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

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.policy.service.IPolicyService;
import org.opengroup.osdu.storage.policy.service.PartitionPolicyStatusService;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class DataAuthorizationService {

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

    public boolean validateOwnerAccess(RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return this.policyService.evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        return this.entitlementsService.hasOwnerAccess(this.headers, recordMetadata.getAcl().getOwners());
    }

    public boolean validateViewerOrOwnerAccess(RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return this.policyService.evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        List<RecordMetadata> postAclCheck = this.entitlementsService.hasValidAccess(Collections.singletonList(recordMetadata), this.headers);
        return postAclCheck != null && !postAclCheck.isEmpty();
    }

    public boolean hasAccess(RecordMetadata recordMetadata, OperationType operationType) {
        if (this.policyEnabled()) {
            return this.policyService.evaluateStorageDataAuthorizationPolicy(recordMetadata, operationType);
        }

        return this.cloudStorage.hasAccess(recordMetadata);
    }

    public boolean policyEnabled() {
        return this.policyService != null && this.statusService.policyEnabled(this.headers.getPartitionId());
    }
}
