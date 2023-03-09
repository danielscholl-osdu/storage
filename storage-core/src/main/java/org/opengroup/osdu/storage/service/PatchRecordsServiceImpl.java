package org.opengroup.osdu.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
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
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.opa.model.OpaError;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.util.CollaborationUtil;
import org.opengroup.osdu.storage.util.RecordBlocks;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.opengroup.osdu.storage.validation.api.PatchInputValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Iterator;
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

    @Autowired
    private RecordBlocks recordBlocks;

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] attributes = new String[0];

    @Override
    public PatchRecordsResponse patchRecords(List<String> recordIds, JsonPatch jsonPatch, String user, Optional<CollaborationContext> collaborationContext) {
        List<String> failedRecordIds = new ArrayList<>();
        List<String> notFoundRecordIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        recordUtil.validateRecordIds(recordIds);
        patchInputValidator.validateDuplicates(jsonPatch);
        patchInputValidator.validateAcls(jsonPatch);
        patchInputValidator.validateKind(jsonPatch);
        patchInputValidator.validateAncestry(jsonPatch);

        boolean dataUpdate = isDataOrMetaBeingUpdated(jsonPatch);

        if(dataUpdate) {
            MultiRecordInfo multiRecordInfo = batchService.getMultipleRecords(new MultiRecordIds(recordIds, attributes), collaborationContext);
            notFoundRecordIds = multiRecordInfo.getInvalidRecords();
            recordIds.removeAll(notFoundRecordIds);

            List<Record> recordsToPersist = new ArrayList<>();
            for(Record validRecord : multiRecordInfo.getRecords()) {
                try {
                    JsonNode patched = jsonPatch.apply(objectMapper.convertValue(validRecord, JsonNode.class));
                    Record patchedRecord = objectMapper.treeToValue(patched, Record.class);
                    recordsToPersist.add(patchedRecord);
                } catch (JsonPatchException e) {
                    recordIds.remove(validRecord.getId());
                    failedRecordIds.add(validRecord.getId());
                    logger.error("Json patch exception when updating record: "+validRecord.getId(), e);
                    errors.add("Json patch error for record: "+validRecord.getId());
                } catch (JsonProcessingException e) {
                    recordIds.remove(validRecord.getId());
                    failedRecordIds.add(validRecord.getId());
                    logger.error("Json processing exception when updating record: "+validRecord.getId(), e);
                    errors.add("Json processing error for record: "+validRecord.getId());
                }
            }
            if(!recordsToPersist.isEmpty()) {
                ingestionService.createUpdateRecords(false, recordsToPersist, user, collaborationContext);
            }
        } else {
            Map<String, RecordMetadata> existingRecords = recordRepository.get(recordIds, collaborationContext);
            if(isOpaEnabled) {
                this.validateUserAccessAndCompliancePolicyConstraints(existingRecords);
            } else {
                this.validateUserAccessAndComplianceConstraints(jsonPatch, recordIds, existingRecords);
            }
            List<RecordMetadata> recordMetadataToBePatched = new ArrayList<>();
            for(String recordId : recordIds) {
                RecordMetadata metadata = existingRecords.get(CollaborationUtil.getIdWithNamespace(recordId, collaborationContext));
                if(metadata == null) {
                    notFoundRecordIds.add(recordId);
                    recordIds.remove(recordId);
                } else {
                    recordMetadataToBePatched.add(metadata);
                }
            }
            if(!recordMetadataToBePatched.isEmpty()) {
                Map<String, String> recordIdPatchError = persistenceService.patchRecordsMetadata(recordMetadataToBePatched, jsonPatch, collaborationContext);
                for(String currentRecordId : recordIdPatchError.keySet()) {
                    recordIds.remove(recordIdPatchError.remove(currentRecordId));
                    errors.add(recordIdPatchError.get(currentRecordId));
                }
            }
        }

        PatchRecordsResponse recordsResponse = PatchRecordsResponse.builder()
                .notFoundRecordIds(notFoundRecordIds)
                .recordIds(recordIds)
                .failedRecordIds(failedRecordIds)
                .errors(errors)
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

    private void validateUserAccessAndCompliancePolicyConstraints(Map<String, RecordMetadata> recordsMetadata) {
        List<RecordMetadata> recordMetadataList = new ArrayList<>(recordsMetadata.values());
        if (!recordMetadataList.isEmpty()) {
            List<ValidationOutputRecord> dataAuthzResult = this.opaService.validateUserAccessToRecords(recordMetadataList, OperationType.update);
            for (ValidationOutputRecord outputRecord : dataAuthzResult) {
                if (!outputRecord.getErrors().isEmpty()) {
                    logger.error(String.format("Data authorization failure for record %s: %s", outputRecord.getId(), outputRecord.getErrors().toString()));
                    for(OpaError error : outputRecord.getErrors()) {
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

    private boolean isDataOrMetaBeingUpdated(JsonPatch jsonPatch) {
        JsonNode patchNode = objectMapper.convertValue(jsonPatch, JsonNode.class);
        Iterator<JsonNode> nodes = patchNode.elements();
        while(nodes.hasNext()) {
            JsonNode currentNode = nodes.next();
            if(currentNode.findPath("path").toString().contains("data") || currentNode.findPath("path").toString().contains("meta"))
                return true;
        }
        return false;
    }
}
