// Copyright 2017-2023, Schlumberger
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.MultiRecordIds;
import org.opengroup.osdu.core.common.model.storage.MultiRecordInfo;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.opa.model.OpaError;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.util.CollaborationUtil;
import org.opengroup.osdu.storage.util.JsonPatchUtil;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.opengroup.osdu.storage.validation.api.PatchInputValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;


@Service
public class PatchRecordsServiceImpl implements PatchRecordsService {

    @Autowired
    private RecordUtil recordUtil;

    @Autowired
    private PatchInputValidator patchInputValidator;

    @Autowired
    private IRecordsMetadataRepository recordRepository;

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private BatchService batchService;

    @Autowired
    private IEntitlementsAndCacheService entitlementsAndCacheService;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private StorageAuditLogger auditLogger;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private IOPAService opaService;

    @Value("#{new Boolean('${opa.enabled}')}")
    private boolean isOpaEnabled;

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] attributes = new String[0];

    @Override
    public PatchRecordsResponse patchRecords(List<String> recordIds, JsonPatch jsonPatch, String user, Optional<CollaborationContext> collaborationContext) {
        List<String> successfulRecordIds = new ArrayList<>();
        List<String> failedRecordIds = new ArrayList<>();
        List<String> notFoundRecordIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        recordUtil.validateRecordIds(recordIds);
        patchInputValidator.validateDuplicates(jsonPatch);
        patchInputValidator.validateAcls(jsonPatch);
        patchInputValidator.validateKind(jsonPatch);
        patchInputValidator.validateAncestry(jsonPatch);

        boolean dataUpdate = JsonPatchUtil.isDataOrMetaBeingUpdated(jsonPatch);

        if (dataUpdate) {
            MultiRecordInfo multiRecordInfo = batchService.getMultipleRecords(new MultiRecordIds(recordIds, attributes), collaborationContext);
            notFoundRecordIds = multiRecordInfo.getInvalidRecords();

            List<Record> recordsToPersist = new ArrayList<>();
            for (Record validRecord : multiRecordInfo.getRecords()) {
                try {
                    Record patchedRecord = JsonPatchUtil.applyPatch(jsonPatch, Record.class, validRecord);
                    if (JsonPatchUtil.isEmptyAclOrLegal(patchedRecord)) {
                        failedRecordIds.add(validRecord.getId());
                        errors.add("Patch operation for record: " + validRecord.getId() + " aborted. Potentially empty value of legaltags or acl/owners or acl/viewers");
                    } else {
                        patchedRecord.getAcl().setOwners(Arrays.stream(patchedRecord.getAcl().getOwners()).distinct().toArray(String[]::new));
                        patchedRecord.getAcl().setViewers(Arrays.stream(patchedRecord.getAcl().getViewers()).distinct().toArray(String[]::new));
                        recordsToPersist.add(patchedRecord);
                    }
                } catch (AppException e) {
                    failedRecordIds.add(validRecord.getId());
                    errors.add("Json patch error for record: " + validRecord.getId());
                }
            }
            logErrors(errors);
            if (!recordsToPersist.isEmpty()) {
                TransferInfo transferInfoAfterPatch = ingestionService.createUpdateRecords(false, recordsToPersist, user, collaborationContext);
                for(Record record : recordsToPersist) {
                    successfulRecordIds.add(record.getId()+":"+transferInfoAfterPatch.getVersion());
                }
            }
        } else {
            Map<String, RecordMetadata> existingRecords = recordRepository.get(recordIds, collaborationContext);
            if (isOpaEnabled) {
                this.validateUserAccessAndCompliancePolicyConstraints(jsonPatch, existingRecords);
            } else {
                this.validateUserAccessAndComplianceConstraints(jsonPatch, recordIds, existingRecords);
            }
            Map<RecordMetadata, JsonPatch> patchPerRecord = new HashMap<>();
            for (String recordId : recordIds) {
                RecordMetadata metadata = existingRecords.get(CollaborationUtil.getIdWithNamespace(recordId, collaborationContext));
                try {
                    if (metadata == null) {
                        /*
                        This condition ensures gracefully handling nonexistent records.
                        */
                        notFoundRecordIds.add(recordId);
                    }
                    else if (JsonPatchUtil.isEmptyAclOrLegal(JsonPatchUtil.applyPatch(jsonPatch, RecordMetadata.class, metadata))) {
                        failedRecordIds.add(recordId);
                        errors.add("Patch operation for record: " + recordId + " aborted. Potentially empty value of legaltags or acl/owners or acl/viewers");
                    }
                    else {
                        metadata.setModifyTime(System.currentTimeMillis());
                        metadata.setModifyUser(user);
                        JsonPatch jsonPatchForRecord;
                        try {
                            jsonPatchForRecord = JsonPatchUtil.getJsonPatchForRecord(metadata, jsonPatch);
                        } catch (IOException e) {
                            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unknown error", "IOException during patch operation");
                        }
                        if (jsonPatchForRecord != null) {
                            patchPerRecord.put(metadata, jsonPatchForRecord);
                        }
                        successfulRecordIds.add(recordId+":"+metadata.getLatestVersion());
                    }
                } catch (AppException e) {
                    failedRecordIds.add(recordId);
                    errors.add("Patch operation for record: " + recordId + " failed with error: " + e.getMessage());
                }
            }
            if (!patchPerRecord.isEmpty()) {
                Map<String, String> recordIdPatchError = persistenceService.patchRecordsMetadata(patchPerRecord, collaborationContext);
                for (String currentRecordId : recordIdPatchError.keySet()) {
                    errors.add(recordIdPatchError.get(currentRecordId));
                }
            }
        }

        PatchRecordsResponse recordsResponse = PatchRecordsResponse.builder()
                .notFoundRecordIds(notFoundRecordIds)
                .recordIds(successfulRecordIds)
                .failedRecordIds(failedRecordIds)
                .errors(errors)
                .recordCount(successfulRecordIds.size()).build();

        auditCreateOrUpdateRecords(recordsResponse);

        return recordsResponse;
    }

    private void logErrors(List<String> errors) {
        if (!errors.isEmpty()) {
            StringBuilder errorBuilder = new StringBuilder();
            for (String error : errors) {
                errorBuilder.append(error).append("|");
            }
            errorBuilder.setLength(errorBuilder.length() - 1);
            logger.error(errorBuilder.toString());
        }
    }

    private void auditCreateOrUpdateRecords(PatchRecordsResponse recordsResponse) {
        List<String> successfulUpdates = recordsResponse.getRecordIds();
        if (!successfulUpdates.isEmpty()) {
            auditLogger.createOrUpdateRecordsSuccess(successfulUpdates);
        }
        List<String> failedUpdates =
                Stream.of(recordsResponse.getNotFoundRecordIds()).flatMap(List::stream).collect(toList());
        if (!failedUpdates.isEmpty()) {
            auditLogger.createOrUpdateRecordsFail(failedUpdates);
        }
    }

    private void validateUserAccessAndComplianceConstraints(
            JsonPatch jsonPatch, List<String> recordIds, Map<String, RecordMetadata> recordsMetadata) {
        patchInputValidator.validateLegalTags(jsonPatch);
        validateOwnerAccess(recordIds, recordsMetadata);
    }

    private void validateUserAccessAndCompliancePolicyConstraints(
    		JsonPatch jsonPatch, Map<String, RecordMetadata> recordsMetadata) {
    	
    	// For the patch operation, we are sending the existing data record and the patched data record to 
        // the data authorization policy for permission evaluation. The user is allowed to do the patch operation
        // when the data authorization policy decides the user has update permission to both data records.
    	
    	// Add the existing data records for the data authorization policy evaluation
        List<RecordMetadata> recordMetadataList = new ArrayList<>(recordsMetadata.values());
        
        for (RecordMetadata metadata : recordsMetadata.values()) {
        	RecordMetadata newRecordMetadata = JsonPatchUtil.applyPatch(jsonPatch, RecordMetadata.class, metadata);
        	if (newRecordMetadata != metadata) {
        		// Add the patched data record for the data authorization policy evaluation
        		recordMetadataList.add(newRecordMetadata);
        	}
        }
        
        if (!recordMetadataList.isEmpty()) {
            List<ValidationOutputRecord> dataAuthzResult = this.opaService.validateUserAccessToRecords(recordMetadataList, OperationType.update);
            for (ValidationOutputRecord outputRecord : dataAuthzResult) {
                if (!outputRecord.getErrors().isEmpty()) {
                    logger.error(String.format("Data authorization failure for record %s: %s", outputRecord.getId(), outputRecord.getErrors().toString()));
                    for (OpaError error : outputRecord.getErrors()) {
                        throw new AppException(Integer.parseInt(error.getCode()), error.getReason(), error.getMessage());
                    }
                }
            }
        }
    }

    private void validateOwnerAccess(List<String> recordIds, Map<String, RecordMetadata> existingRecords) {
        for (String recordId : recordIds) {
            RecordMetadata metadata = existingRecords.get(recordId);

            if (metadata == null) {
                continue;
            }

            // pre acl check, enforce application data restriction
            if (!this.entitlementsAndCacheService.hasOwnerAccess(this.headers, metadata.getAcl().getOwners())) {
                this.logger.warning(String.format("User does not have owner access to record %s", recordId));
                throw new AppException(HttpStatus.SC_FORBIDDEN, "User Unauthorized", "User is not authorized to update records.");
            }
        }
    }
}
