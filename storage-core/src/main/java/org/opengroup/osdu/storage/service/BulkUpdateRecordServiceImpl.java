// Copyright 2017-2021, Schlumberger
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

import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.core.common.storage.IPersistenceService;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.policy.service.IPolicyService;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.response.BulkUpdateRecordsResponse;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.opengroup.osdu.storage.validation.api.PatchOperationValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Service
public class BulkUpdateRecordServiceImpl implements BulkUpdateRecordService {

    @Autowired
    private IRecordsMetadataRepository recordRepository;

    @Autowired
    private PatchOperationValidator patchOperationValidator;

    @Autowired
    private IEntitlementsAndCacheService entitlementsAndCacheService;

    @Autowired
    private StorageAuditLogger auditLogger;

    @Autowired
    private IPersistenceService persistenceService;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private RecordUtil recordUtil;

    @Autowired
    private Clock clock;

    @Autowired
    private DataAuthorizationService dataAuthorizationService;

    @Autowired(required = false)
    private IPolicyService policyService;

    @Override
    public BulkUpdateRecordsResponse bulkUpdateRecords(RecordBulkUpdateParam recordBulkUpdateParam, String user) {
        List<RecordMetadata> validRecordsMetadata = new ArrayList<>();
        List<String> validRecordsId = new ArrayList<>();
        List<String> lockedRecordsId = new ArrayList<>();

        RecordQuery bulkUpdateQuery = recordBulkUpdateParam.getQuery();
        List<PatchOperation> bulkUpdateOps = recordBulkUpdateParam.getOps();

        List<String> ids = bulkUpdateQuery.getIds();

        // validate record ids and properties
        this.recordUtil.validateRecordIds(ids);
        this.patchOperationValidator.validateDuplicates(bulkUpdateOps);
        this.patchOperationValidator.validateAcls(bulkUpdateOps);
        this.patchOperationValidator.validateTags(bulkUpdateOps);

        //<idWithoutVersion, idWithVersion>
        Map<String, String> idMap = recordUtil.mapRecordsAndVersions(ids);
        List<String> idsWithoutVersion = new ArrayList<>(idMap.keySet());
        Map<String, RecordMetadata> existingRecords = recordRepository.get(idsWithoutVersion);
        List<String> notFoundRecordIds = new ArrayList<>();
        List<String> unauthorizedRecordIds= this.dataAuthorizationService.policyEnabled()
                ? this.validateUserAccessAndCompliancePolicyConstraints(bulkUpdateOps, idMap, existingRecords, user)
                : this.validateUserAccessAndComplianceConstraints(bulkUpdateOps, idMap, existingRecords);

        final long currentTimestamp = clock.millis();
        for (String id : idsWithoutVersion) {
            String idWithVersion = idMap.get(id);
            RecordMetadata metadata = existingRecords.get(id);

            if (metadata == null) {
                notFoundRecordIds.add(idWithVersion);
                ids.remove(idWithVersion);
            } else {
                if (unauthorizedRecordIds.contains(idWithVersion)) {
                    ids.remove(idWithVersion);
                } else {
                    metadata = recordUtil.updateRecordMetaDataForPatchOperations(metadata, bulkUpdateOps, user, currentTimestamp);
                    validRecordsMetadata.add(metadata);
                    validRecordsId.add(id);
                }
            }
        }

        if (!validRecordsId.isEmpty()) {
            lockedRecordsId = persistenceService.updateMetadata(validRecordsMetadata, validRecordsId, idMap);
        }

        for (String lockedId : lockedRecordsId) {
            ids.remove(lockedId);
        }

        BulkUpdateRecordsResponse recordsResponse = BulkUpdateRecordsResponse.builder()
                .notFoundRecordIds(notFoundRecordIds)
                .unAuthorizedRecordIds(unauthorizedRecordIds)
                .recordIds(ids)
                .lockedRecordIds(lockedRecordsId)
                .recordCount(ids.size()).build();

        auditCreateOrUpdateRecordsFails(recordsResponse);

        return recordsResponse;
    }

    private void auditCreateOrUpdateRecordsFails(BulkUpdateRecordsResponse recordsResponse) {
        List<String> failedUpdates =
                Stream.of(recordsResponse.getNotFoundRecordIds(), recordsResponse.getUnAuthorizedRecordIds(),
                        recordsResponse.getLockedRecordIds()).flatMap(List::stream).collect(toList());
        if (!failedUpdates.isEmpty()) {
            auditLogger.createOrUpdateRecordsFail(failedUpdates);
        }
    }

    private List<String> validateUserAccessAndComplianceConstraints(
            List<PatchOperation> bulkUpdateOps, Map<String, String> idMap, Map<String, RecordMetadata> existingRecords) {
        this.patchOperationValidator.validateLegalTags(bulkUpdateOps);
        return this.validateOwnerAccess(idMap, existingRecords);
    }

    private List<String> validateOwnerAccess(Map<String, String> idMap, Map<String, RecordMetadata> existingRecords) {
        List<String> unauthorizedRecordIds = new ArrayList<>();
        for (String id : idMap.keySet()) {
            String idWithVersion = idMap.get(id);
            RecordMetadata metadata = existingRecords.get(id);

            if (metadata == null) {
                continue;
            }

            // pre acl check, enforce application data restriction
            if (!this.entitlementsAndCacheService.hasOwnerAccess(this.headers, metadata.getAcl().getOwners())) {
                unauthorizedRecordIds.add(idWithVersion);
            }
        }
        return unauthorizedRecordIds;
    }

    private List<String> validateUserAccessAndCompliancePolicyConstraints(
            List<PatchOperation> bulkUpdateOps, Map<String, String> idMap, Map<String, RecordMetadata> existingRecords, String user) {
        List<String> unauthorizedRecordIds = new ArrayList<>();
        final long currentTimestamp = clock.millis();
        for (String id : idMap.keySet()) {
            String idWithVersion = idMap.get(id);
            RecordMetadata metadata = existingRecords.get(id);

            if (metadata == null) continue;

            metadata = this.recordUtil.updateRecordMetaDataForPatchOperations(metadata, bulkUpdateOps, user, currentTimestamp);

            if (!this.policyService.evaluateStorageDataAuthorizationPolicy(metadata, OperationType.update)) {
                unauthorizedRecordIds.add(idWithVersion);
            }
        }
        return unauthorizedRecordIds;
    }
}
