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

import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
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
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.opengroup.osdu.storage.util.RecordConstants.OPA_FEATURE_NAME;

@Service
public class BulkUpdateRecordServiceImpl implements BulkUpdateRecordService {

    @Autowired
    private IRecordsMetadataRepository recordRepository;

    @Autowired
    private PatchOperationValidator patchOperationValidator;

    @Autowired
    private IEntitlementsExtensionService entitlementsAndCacheService;

    @Autowired
    private StorageAuditLogger auditLogger;

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private RecordUtil recordUtil;

    @Autowired
    private Clock clock;

    @Autowired
    private IOPAService opaService;

    @Autowired
    private IFeatureFlag featureFlag;

    @Override
    public BulkUpdateRecordsResponse bulkUpdateRecords(RecordBulkUpdateParam recordBulkUpdateParam, String user, Optional<CollaborationContext> collaborationContext) {
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

        Map<String, String> idMap = ids.stream().collect(Collectors.toMap(identity(), identity()));
        List<String> idsWithoutVersion = new ArrayList<>(idMap.keySet());
        Map<String, RecordMetadata> existingRecords = recordRepository.get(idsWithoutVersion, collaborationContext);
        List<String> notFoundRecordIds = new ArrayList<>();
        List<String> unauthorizedRecordIds = featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)
                ? this.validateUserAccessAndCompliancePolicyConstraints(bulkUpdateOps, idMap, existingRecords, user)
                : this.validateUserAccessAndComplianceConstraints(bulkUpdateOps, idMap, existingRecords);

        final long currentTimestamp = clock.millis();
        for (String id : idsWithoutVersion) {
            String idWithVersion = idMap.get(id);
            RecordMetadata metadata = existingRecords.get(CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext));

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
            lockedRecordsId = persistenceService.updateMetadata(validRecordsMetadata, validRecordsId, idMap, collaborationContext);
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

        auditCreateOrUpdateRecords(recordsResponse);

        return recordsResponse;
    }

    private void auditCreateOrUpdateRecords(BulkUpdateRecordsResponse recordsResponse) {
        List<String> successfulUpdates = recordsResponse.getRecordIds();
        if (!successfulUpdates.isEmpty()) {
            auditLogger.createOrUpdateRecordsSuccess(successfulUpdates);
        }
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
        boolean isDataManager = this.entitlementsAndCacheService.isDataManager(this.headers);
        List<String> unauthorizedRecordIds = new ArrayList<>();
        for (String id : idMap.keySet()) {
            String idWithVersion = idMap.get(id);
            RecordMetadata metadata = existingRecords.get(id);

            if (metadata == null) {
                continue;
            }

            // pre acl check, enforce application data restriction
            if (!isDataManager && !this.entitlementsAndCacheService.hasOwnerAccess(this.headers, metadata.getAcl().getOwners())) {
                unauthorizedRecordIds.add(idWithVersion);
            }
        }
        return unauthorizedRecordIds;
    }

    private List<String> validateUserAccessAndCompliancePolicyConstraints(
            List<PatchOperation> bulkUpdateOps, Map<String, String> idMap, Map<String, RecordMetadata> existingRecords, String user) {
        List<String> unauthorizedRecordIds = new ArrayList<>();
        final long currentTimestamp = clock.millis();
        List<RecordMetadata> updatedRecordsMetadata = new ArrayList<>();
        for (String id : idMap.keySet()) {
            String idWithVersion = idMap.get(id);
            RecordMetadata metadata = existingRecords.get(id);
            if (metadata == null) continue;
            
            // For the patch operation, we are sending the existing data record and the patched data record to 
            // the data authorization policy for permission evaluation. The user is allowed to do the patch operation
            // when the data authorization policy decides the user has update permission to both data records. 

            // Add the existing data record for the data authorization policy evaluation
            updatedRecordsMetadata.add(metadata);
            RecordMetadata newMetadata = this.recordUtil.updateRecordMetaDataForPatchOperations(metadata, bulkUpdateOps, user, currentTimestamp);
            if (newMetadata != metadata) {
            	// Add the patched data record for the data authorization policy evaluation
            	updatedRecordsMetadata.add(newMetadata);
            }
        }

        if (!updatedRecordsMetadata.isEmpty()) {
            List<ValidationOutputRecord> dataAuthzResult = this.opaService.validateUserAccessToRecords(updatedRecordsMetadata, OperationType.update);
            for (ValidationOutputRecord outputRecord : dataAuthzResult) {
                if (!outputRecord.getErrors().isEmpty()) {
                    String idWithVersion = idMap.get(outputRecord.getId());
                    unauthorizedRecordIds.add(idWithVersion);
                }
            }
        }

        return unauthorizedRecordIds;
    }
}
