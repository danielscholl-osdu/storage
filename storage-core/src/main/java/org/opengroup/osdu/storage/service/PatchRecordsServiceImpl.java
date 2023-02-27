package org.opengroup.osdu.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.util.CollaborationUtil;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.opengroup.osdu.storage.validation.api.PatchInputValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
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

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] attributes = new String[0];

    @Override
    public PatchRecordsResponse patchRecords(List<String> recordIds, JsonPatch jsonPatch, String user, Optional<CollaborationContext> collaborationContext) {
        if(recordIds.isEmpty())
            return PatchRecordsResponse.builder().recordCount(0).build();

        List<String> failedRecordIds = new ArrayList<>();
        List<String> unauthorizedRecordIds = new ArrayList<>();
        List<String> lockedRecordsId = new ArrayList<>();

        // validate record ids and metadata properties if they are being patched (acl, legalTags, kind, ancestry, etc)
        recordUtil.validateRecordIds(recordIds);
        patchInputValidator.validateDuplicates(jsonPatch);
        patchInputValidator.validateAcls(jsonPatch);
        patchInputValidator.validateKind(jsonPatch);
        patchInputValidator.validateAncestry(jsonPatch);

        Map<String, String> idMap = recordIds.stream().collect(Collectors.toMap(identity(), identity()));
        List<String> idsWithoutVersion = new ArrayList<>(idMap.keySet());

        MultiRecordInfo multiRecordInfo = batchService.getMultipleRecords(new MultiRecordIds(idsWithoutVersion, attributes), collaborationContext);

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
            } catch (JsonProcessingException e) {
                recordIds.remove(validRecord.getId());
                failedRecordIds.add(validRecord.getId());
                logger.error("Json processing exception when updating record: "+validRecord.getId(), e);
            }
        }

        boolean dataUpdate = isDataOrMetaBeingUpdated(jsonPatch);
        if(dataUpdate) {
            TransferInfo recordsUpdateResponse = ingestionService.createUpdateRecords(false, recordsToPersist, user, collaborationContext);
            // what category is recordsUpdateResponse.getSkippedRecords()?? (skipped/failed records)
        } else {
            Map<String, RecordMetadata> existingRecordsMetadata = getRecordsMetadataFromRecords(multiRecordInfo.getRecords(), false, user);
            Map<String, RecordMetadata> updatedRecordsMetadata = getRecordsMetadataFromRecords(recordsToPersist, true, user);
            unauthorizedRecordIds = isOpaEnabled
                    ? this.validateUserAccessAndCompliancePolicyConstraints(idMap, updatedRecordsMetadata)
                    : this.validateUserAccessAndComplianceConstraints(jsonPatch, idMap, existingRecordsMetadata);
            if (!recordIds.isEmpty()) {
                lockedRecordsId = persistenceService.updateMetadata(new ArrayList<>(updatedRecordsMetadata.values()), recordIds, idMap, collaborationContext);
                recordIds.removeAll(lockedRecordsId);
            }
        }

        PatchRecordsResponse recordsResponse = PatchRecordsResponse.builder()
                .notFoundRecordIds(multiRecordInfo.getInvalidRecords())
                .unAuthorizedRecordIds(unauthorizedRecordIds)
                .recordIds(recordIds)
                .lockedRecordIds(lockedRecordsId)
                .failedRecordIds(failedRecordIds)
                .recordCount(recordIds.size()).build();

        auditCreateOrUpdateRecords(recordsResponse);

        return recordsResponse;
    }

    private Map<String, RecordMetadata> getRecordsMetadataFromRecords(List<Record> records, boolean isUpdate, String user) {
        Map<String, RecordMetadata> recordsMetadata = new HashMap<>();
        for(Record record : records) {
            RecordMetadata recordMetadata = new RecordMetadata();
            recordMetadata.setId(record.getId());
            recordMetadata.setAcl(record.getAcl());
            recordMetadata.setTags(record.getTags());
            recordMetadata.setLegal(record.getLegal());
            recordMetadata.setAncestry(record.getAncestry());
            recordMetadata.setKind(record.getKind());
            if(isUpdate) {
                long currentTimestamp = clock.millis();
                recordMetadata.setModifyUser(user);
                recordMetadata.setModifyTime(currentTimestamp);
            }
            recordsMetadata.put(record.getId(), recordMetadata);
        }
        return recordsMetadata;
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
            JsonPatch jsonPatch, Map<String, String> idMap, Map<String, RecordMetadata> existingRecords) {
        patchInputValidator.validateLegalTags(jsonPatch);
        return validateOwnerAccess(idMap, existingRecords);
    }

    private List<String> validateUserAccessAndCompliancePolicyConstraints(
            Map<String, String> idMap, Map<String, RecordMetadata> recordsMetadata) {
        List<String> unauthorizedRecordIds = new ArrayList<>();

        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        for (String id : idMap.keySet()) {
            recordMetadataList.add(recordsMetadata.get(id));
        }

        if (!recordMetadataList.isEmpty()) {
            List<ValidationOutputRecord> dataAuthzResult = this.opaService.validateUserAccessToRecords(recordMetadataList, OperationType.update);
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

    private boolean isDataOrMetaBeingUpdated(JsonPatch jsonPatch) {
        JsonNode patchNode = objectMapper.convertValue(jsonPatch, JsonNode.class);
        return (patchNode.has("data") || patchNode.has("meta"));
    }
}
