package org.opengroup.osdu.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.MultiRecordIds;
import org.opengroup.osdu.core.common.model.storage.MultiRecordInfo;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.PatchRecordsRequestModel;
import org.opengroup.osdu.storage.model.RecordPatchOperation;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.util.CollaborationUtil;
import org.opengroup.osdu.storage.util.api.PatchUtil;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.opengroup.osdu.storage.validation.api.PatchOperationValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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


@Service
public class PatchRecordsServiceImpl implements PatchRecordsService {

    @Autowired
    private RecordUtil recordUtil;

    @Autowired
    private PatchUtil patchUtil;

    @Autowired
    private PatchOperationValidator patchOperationValidator;

    @Autowired
    private IRecordsMetadataRepository recordRepository;

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private BatchService batchService;

    @Autowired
    private Clock clock;

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

    @Override
    public PatchRecordsResponse patchRecords(PatchRecordsRequestModel patchRecordsRequest, String user, Optional<CollaborationContext> collaborationContext) {
        List<String> lockedRecordsId = new ArrayList<>();
        List<String> failedRecordIds = new ArrayList<>();
        List<String> notFoundRecordIds;
        List<String> unauthorizedRecordIds;

        boolean dataUpdate = false;

        List<PatchOperation> patchOperations = new ArrayList<>();
        for(RecordPatchOperation recordPatchOperation : patchRecordsRequest.getOps()) {
            patchOperations.add(PatchOperation.builder()
                    .op(recordPatchOperation.getOp())
                    .path(recordPatchOperation.getPath())
                    .value(recordPatchOperation.getValue())
                    .build());
            if(recordPatchOperation.getPath().startsWith("/data") || recordPatchOperation.getPath().startsWith("/meta"))
                dataUpdate = true;
        }

        List<String> recordIds = patchRecordsRequest.getQuery().getIds();
        // validate record ids and properties
        recordUtil.validateRecordIds(recordIds);
        patchOperationValidator.validateAcls(patchOperations);
        patchOperationValidator.validateTags(patchOperations);
        //validate kind?
        //validate ancestry?

        Map<String, String> idMap = recordIds.stream().collect(Collectors.toMap(identity(), identity()));
        List<String> idsWithoutVersion = new ArrayList<>(idMap.keySet());
        Map<String, JsonPatch> jsonPatchForRecordIds = patchUtil.convertPatchOpsToJsonPatch(recordIds, patchRecordsRequest.getOps(), collaborationContext);

        if(dataUpdate) {
            String[] attributes = {};
            MultiRecordIds multiRecordIds = new MultiRecordIds(idsWithoutVersion, attributes);
            //TODO: should we set a max limit of 100 records on the input list, since query records API, which also uses
            //getMultipleRecords from batchService, has a limit of 100 records at a time
            MultiRecordInfo multiRecordInfo = batchService.getMultipleRecords(multiRecordIds, collaborationContext);
            notFoundRecordIds = multiRecordInfo.getInvalidRecords();

            //validate owner access before patch
            Map<String, RecordMetadata> existingRecords = recordRepository.get(idsWithoutVersion, collaborationContext);
            unauthorizedRecordIds = isOpaEnabled
                    ? this.validateUserAccessAndCompliancePolicyConstraints(patchOperations, idMap, existingRecords, user)
                    : this.validateUserAccessAndComplianceConstraints(patchOperations, idMap, existingRecords);

            List<Record> recordsToPersist = new ArrayList<>();
            for(Record validRecord : multiRecordInfo.getRecords()) {
                try {
                    JsonPatch patchOperationForRecord = jsonPatchForRecordIds.get(validRecord.getId());
                    JsonNode patched = patchOperationForRecord.apply(objectMapper.convertValue(validRecord, JsonNode.class));
                    Record patchedRecord = objectMapper.treeToValue(patched, Record.class);
                    recordsToPersist.add(patchedRecord);
                } catch (JsonPatchException e) {
                    failedRecordIds.add(validRecord.getId());
                    logger.error("Json patch exception when updating record: "+validRecord.getId(), e);
                } catch (JsonProcessingException e) {
                    failedRecordIds.add(validRecord.getId());
                    logger.error("Json processing exception when updating record: "+validRecord.getId(), e);
                }
            }
            //TODO: how to set lockedRecordsId?

            ingestionService.createUpdateRecords(false, recordsToPersist, user, collaborationContext);
        } else {
            List<RecordMetadata> validRecordsMetadata = new ArrayList<>();
            List<String> validRecordsId = new ArrayList<>();
            Map<String, RecordMetadata> existingRecords = recordRepository.get(idsWithoutVersion, collaborationContext);
            notFoundRecordIds = new ArrayList<>();
            unauthorizedRecordIds= isOpaEnabled
                    ? this.validateUserAccessAndCompliancePolicyConstraints(patchOperations, idMap, existingRecords, user)
                    : this.validateUserAccessAndComplianceConstraints(patchOperations, idMap, existingRecords);

            final long currentTimestamp = clock.millis();
            for (String id : idsWithoutVersion) {
                String idWithVersion = idMap.get(id);
                RecordMetadata metadata = existingRecords.get(CollaborationUtil.getIdWithNamespace(id, collaborationContext));

                if (metadata == null) {
                    notFoundRecordIds.add(idWithVersion);
                    recordIds.remove(idWithVersion);
                } else {
                    if (unauthorizedRecordIds.contains(idWithVersion)) {
                        recordIds.remove(idWithVersion);
                    } else {
                        try {
                            JsonPatch patchOperationForRecord = jsonPatchForRecordIds.get(id);
                            JsonNode patched = patchOperationForRecord.apply(objectMapper.convertValue(metadata, JsonNode.class));
                            RecordMetadata patchedRecord = objectMapper.treeToValue(patched, RecordMetadata.class);
                            patchedRecord.setModifyUser(user);
                            patchedRecord.setModifyTime(currentTimestamp);
                            validRecordsMetadata.add(patchedRecord);
                            validRecordsId.add(id);
                        } catch (JsonPatchException e) {
                            failedRecordIds.add(id);
                            logger.error("Json patch exception when updating record: "+id, e);
                        } catch (JsonProcessingException e) {
                            failedRecordIds.add(id);
                            logger.error("Json processing exception when updating record: "+id, e);
                        }
                    }
                }
            }

            if (!validRecordsId.isEmpty()) {
                lockedRecordsId = persistenceService.updateMetadata(validRecordsMetadata, validRecordsId, idMap, collaborationContext);
            }

            for (String lockedId : lockedRecordsId) {
                recordIds.remove(lockedId);
            }
        }


        PatchRecordsResponse recordsResponse = PatchRecordsResponse.builder()
                .notFoundRecordIds(notFoundRecordIds)
                .unAuthorizedRecordIds(unauthorizedRecordIds)
                .recordIds(recordIds)
                .lockedRecordIds(lockedRecordsId)
                .failedRecordIds(failedRecordIds)
                .recordCount(recordIds.size()).build();

        auditCreateOrUpdateRecords(recordsResponse);

        return recordsResponse;
    }

    private void auditCreateOrUpdateRecords(PatchRecordsResponse recordsResponse) {
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
            List<PatchOperation> patchOps, Map<String, String> idMap, Map<String, RecordMetadata> existingRecords) {
        this.patchOperationValidator.validateLegalTags(patchOps);
        return this.validateOwnerAccess(idMap, existingRecords);
    }

    private List<String> validateUserAccessAndCompliancePolicyConstraints(
            List<PatchOperation> patchOps, Map<String, String> idMap, Map<String, RecordMetadata> existingRecords, String user) {
        List<String> unauthorizedRecordIds = new ArrayList<>();
        List<RecordMetadata> updatedRecordsMetadata = new ArrayList<>();
        for (String id : idMap.keySet()) {
            RecordMetadata metadata = existingRecords.get(id);
            if (metadata == null) continue;

            //TODO: Why do we update metadata here in the original patch impl? Find out if we really need this
            //metadata = this.recordUtil.updateRecordMetaDataForPatchOperations(metadata, patchOps, user, currentTimestamp);
            updatedRecordsMetadata.add(metadata);
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
}
