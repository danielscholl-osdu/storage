package org.opengroup.osdu.storage.service;

import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
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

import static java.util.function.Function.identity;


@Service
public class PatchRecordsServiceImpl implements PatchRecordsService {

    @Autowired
    private RecordUtil recordUtil;

    @Autowired
    private PatchOperationValidator patchOperationValidator;

    @Autowired
    private IRecordsMetadataRepository recordRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private IOPAService opaService;

    @Value("${opa.enabled}")
    private boolean isOpaEnabled;

    @Autowired
    private IEntitlementsAndCacheService entitlementsAndCacheService;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private PatchUtil patchUtil;

    @Override
    public PatchRecordsResponse patchRecords(PatchRecordsRequestModel patchRecordsRequest, String user, Optional<CollaborationContext> collaborationContext) {
        List<String> validRecordsId = new ArrayList<>();
        List<String> lockedRecordsId = new ArrayList<>();

        List<PatchOperation> patchOperations = new ArrayList<>();
        for(RecordPatchOperation recordPatchOperation : patchRecordsRequest.getOps()) {
            patchOperations.add(PatchOperation.builder()
                    .op(recordPatchOperation.getOp())
                    .path(recordPatchOperation.getPath())
                    .value(recordPatchOperation.getValue())
                    .build());
        }

        List<String> recordIds = patchRecordsRequest.getQuery().getIds();

        // validate record ids and properties
        recordUtil.validateRecordIds(recordIds);
        patchOperationValidator.validateAcls(patchOperations);
        patchOperationValidator.validateTags(patchOperations);

        Map<String, String> idMap = recordIds.stream().collect(Collectors.toMap(identity(), identity()));
        List<String> idsWithoutVersion = new ArrayList<>(idMap.keySet());
        Map<String, RecordMetadata> existingRecords = recordRepository.get(idsWithoutVersion, collaborationContext);
        List<String> notFoundRecordIds = new ArrayList<>();
        List<String> unauthorizedRecordIds= isOpaEnabled
                ? this.validateUserAccessAndCompliancePolicyConstraints(patchOperations, idMap, existingRecords, user)
                : this.validateUserAccessAndComplianceConstraints(patchOperations, idMap, existingRecords);

        final long currentTimestamp = clock.millis();

        JsonPatch patchPayload = patchUtil.convertPatchOpsToJsonPatch(patchRecordsRequest);

        for (String id : idsWithoutVersion) {
            String idWithVersion = idMap.get(id);
            RecordMetadata metadata = existingRecords.get(CollaborationUtil.getIdWithNamespace(id, collaborationContext));
        }

        //TODO: validate entitlements (user access) and compliance constraints on records received

        //TODO implement common patch logic

        return null;
    }

    private List<String> validateUserAccessAndComplianceConstraints(
            List<PatchOperation> patchOps, Map<String, String> idMap, Map<String, RecordMetadata> existingRecords) {
        this.patchOperationValidator.validateLegalTags(patchOps);
        return this.validateOwnerAccess(idMap, existingRecords);
    }

    private List<String> validateUserAccessAndCompliancePolicyConstraints(
            List<PatchOperation> patchOps, Map<String, String> idMap, Map<String, RecordMetadata> existingRecords, String user) {
        List<String> unauthorizedRecordIds = new ArrayList<>();
        final long currentTimestamp = clock.millis();
        List<RecordMetadata> updatedRecordsMetadata = new ArrayList<>();
        for (String id : idMap.keySet()) {
            RecordMetadata metadata = existingRecords.get(id);
            if (metadata == null) continue;

            metadata = this.recordUtil.updateRecordMetaDataForPatchOperations(metadata, patchOps, user, currentTimestamp);
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
