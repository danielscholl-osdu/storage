// Copyright 2017-2019, Schlumberger
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
import com.google.common.base.Strings;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.util.JsonPatchUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.opengroup.osdu.storage.util.RecordConstants.COLLABORATIONS_FEATURE_NAME;

@Service
public class PersistenceServiceImpl implements PersistenceService {

    @Autowired
    private IRecordsMetadataRepository recordRepository;

    @Autowired
    private ICloudStorage cloudStorage;

    @Autowired
    private IMessageBus pubSubClient;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private JaxRsDpsLog logger;
    @Autowired
    private IFeatureFlag collaborationFeatureFlag;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void persistRecordBatch(TransferBatch transfer, Optional<CollaborationContext> collaborationContext) {

        List<RecordProcessing> recordsProcessing = transfer.getRecords();
        List<RecordMetadata> recordsMetadata = new ArrayList<>(recordsProcessing.size());

        PubSubInfo[] pubsubInfo = new PubSubInfo[recordsProcessing.size()];
        RecordChangedV2[] recordChangedV2 = new RecordChangedV2[recordsProcessing.size()];

        for (int i = 0; i < recordsProcessing.size(); i++) {
            RecordProcessing processing = recordsProcessing.get(i);
            RecordMetadata recordMetadata = processing.getRecordMetadata();
            recordsMetadata.add(recordMetadata);
            if (processing.getOperationType() == OperationType.create) {
                pubsubInfo[i] = getPubSubInfo(recordMetadata, OperationType.create);
                recordChangedV2[i] = getRecordChangedV2(recordMetadata, OperationType.create);
            } else {
                pubsubInfo[i] = getPubSubInfo(recordMetadata, OperationType.update);
                pubsubInfo[i].setRecordBlocks(processing.getRecordBlocks());
                recordChangedV2[i] = getRecordChangedV2(recordMetadata, OperationType.update);
                recordChangedV2[i].setRecordBlocks(processing.getRecordBlocks());

                if (!Strings.isNullOrEmpty(processing.getRecordMetadata().getPreviousVersionKind())) {
                    pubsubInfo[i].setPreviousVersionKind(processing.getRecordMetadata().getPreviousVersionKind());
                    recordChangedV2[i].setPreviousVersionKind(processing.getRecordMetadata().getPreviousVersionKind());
                }
            }
        }

        this.commitBatch(recordsProcessing, recordsMetadata, collaborationContext);
        if (collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)) {
            this.pubSubClient.publishMessage(collaborationContext, this.headers, recordChangedV2);
        }
        if (!collaborationContext.isPresent()) {
            this.pubSubClient.publishMessage(this.headers, pubsubInfo);
        }
    }

    private void commitBatch(List<RecordProcessing> recordsProcessing, List<RecordMetadata> recordsMetadata, Optional<CollaborationContext> collaborationContext) {

        try {
            this.commitCloudStorageTransaction(recordsProcessing);
            this.commitDatastoreTransaction(recordsMetadata, collaborationContext);
        } catch (AppException e) {
            try {
                //try deleting the latest version of the record from blob storage and Datastore
                this.tryCleanupCloudStorage(recordsProcessing);
                this.tryCleanupDatastore(recordsMetadata, collaborationContext);
            } catch (AppException innerException) {
                e.addSuppressed(innerException);
            }

            throw e;
        }
    }

    @Override
    public List<String> updateMetadata(List<RecordMetadata> recordMetadata, List<String> recordsId, Map<String, String> recordsIdMap, Optional<CollaborationContext> collaborationContext) {
        Map<String, Acl> originalAcls = new HashMap<>();
        List<String> lockedRecords = new ArrayList<>();
        List<RecordMetadata> validMetadata = new ArrayList<>();
        try {
            originalAcls = this.cloudStorage.updateObjectMetadata(recordMetadata, recordsId, validMetadata, lockedRecords, recordsIdMap, collaborationContext);
            this.commitDatastoreTransaction(validMetadata, collaborationContext);
        } catch (NotImplementedException e) {
            throw new AppException(HttpStatus.SC_NOT_IMPLEMENTED, "Not Implemented", "Interface not fully implemented yet");
        } catch (Exception e) {
            this.logger.warning("Reverting meta data changes");
            try {
                this.cloudStorage.revertObjectMetadata(recordMetadata, originalAcls, collaborationContext);
            } catch (NotImplementedException innerEx) {
                throw new AppException(HttpStatus.SC_NOT_IMPLEMENTED, "Not Implemented", "Interface not fully implemented yet");
            } catch (Exception innerEx) {
                e.addSuppressed(innerEx);
            }
            throw e;
        }
        PubSubInfo[] pubsubInfo = new PubSubInfo[recordMetadata.size()];
        RecordChangedV2[] recordChangedV2 = new RecordChangedV2[recordMetadata.size()];
        for (int i = 0; i < recordMetadata.size(); i++) {
            RecordMetadata metadata = recordMetadata.get(i);
            pubsubInfo[i] = getPubSubInfo(metadata, OperationType.update);
            recordChangedV2[i] = getRecordChangedV2(metadata, OperationType.update);
        }
        if (collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)) {
            this.pubSubClient.publishMessage(collaborationContext, this.headers, recordChangedV2);
        }
        if (!collaborationContext.isPresent()) {
            this.pubSubClient.publishMessage(this.headers, pubsubInfo);
        }

        return lockedRecords;
    }

    @Override
    public Map<String, String> patchRecordsMetadata(Map<RecordMetadata, JsonPatch> jsonPatchPerRecord, Optional<CollaborationContext> collaborationContext) {
        Map<String, String> recordError;
        try {
            recordError = this.commitPatchDatastoreTransaction(jsonPatchPerRecord, collaborationContext);
        } catch (NotImplementedException e) {
            throw new AppException(HttpStatus.SC_NOT_IMPLEMENTED, "Not Implemented", "Interface not fully implemented yet");
        } catch (Exception e) {
            this.logger.warning("Reverting meta data changes");
            try {
                this.commitDatastoreTransaction(new ArrayList<>(jsonPatchPerRecord.keySet()), collaborationContext);
            } catch (NotImplementedException innerEx) {
                throw new AppException(HttpStatus.SC_NOT_IMPLEMENTED, "Not Implemented", "Interface not fully implemented yet");
            } catch (Exception innerEx) {
                logger.error("Error reverting metadata to its original state", innerEx);
                e.addSuppressed(innerEx);
            }
            throw e;
        }
        if (!recordError.isEmpty()) {
            this.logger.warning("Reverting meta data changes");
            try {
                this.commitDatastoreTransaction(new ArrayList<>(jsonPatchPerRecord.keySet()), collaborationContext);
            } catch (NotImplementedException innerEx) {
                throw new AppException(HttpStatus.SC_NOT_IMPLEMENTED, "Not Implemented", "Interface not fully implemented yet");
            } catch (Exception e) {
                logger.error("Error reverting metadata to its original state", e);
                throw e;
            }
        } else {
            List<PubSubInfo> pubSubInfos = new ArrayList<>();
            List<RecordChangedV2> recordChangedV2s = new ArrayList<>();
            for (RecordMetadata metadata : jsonPatchPerRecord.keySet()) {
                PubSubInfo pubSubInfo = getPubSubInfo(metadata, OperationType.update);
                RecordChangedV2 recordChangedV2 = getRecordChangedV2(metadata, OperationType.update);
                JsonPatch jsonPatchForRecord = jsonPatchPerRecord.get(metadata);
                if (JsonPatchUtil.isKindBeingUpdated(jsonPatchForRecord)) {
                    String newKind = JsonPatchUtil.getNewKindFromPatchInput(jsonPatchForRecord);
                    pubSubInfo.setPreviousVersionKind(metadata.getKind());
                    pubSubInfo.setKind(newKind);
                    recordChangedV2.setPreviousVersionKind(metadata.getKind());
                    recordChangedV2.setKind(newKind);
                }
                pubSubInfos.add(pubSubInfo);
                recordChangedV2s.add(recordChangedV2);
            }
            if (collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)) {
                this.pubSubClient.publishMessage(collaborationContext, this.headers, recordChangedV2s.stream().toArray(RecordChangedV2[]::new));
            }
            if (!collaborationContext.isPresent()) {
                this.pubSubClient.publishMessage(this.headers, pubSubInfos.stream().toArray(PubSubInfo[]::new));
            }
        }
        return recordError;
    }

    private PubSubInfo getPubSubInfo(RecordMetadata recordMetadata, OperationType operationType) {
        return PubSubInfo.builder()
                .id(recordMetadata.getId())
                .kind(recordMetadata.getKind())
                .op(operationType)
                .build();
    }

    private RecordChangedV2 getRecordChangedV2(RecordMetadata recordMetadata, OperationType operationType) {
        return RecordChangedV2.builder()
                .id(recordMetadata.getId())
                .version(recordMetadata.getLatestVersion())
                .modifiedBy(recordMetadata.getModifyUser())
                .kind(recordMetadata.getKind())
                .op(operationType)
                .build();
    }

    private void tryCleanupCloudStorage(List<RecordProcessing> recordsProcessing) {
        recordsProcessing.forEach(r -> this.cloudStorage.deleteVersion(r.getRecordMetadata(), r.getRecordMetadata().getLatestVersion()));
    }

    private void tryCleanupDatastore(List<RecordMetadata> recordsMetadata, Optional<CollaborationContext> collaborationContext) {
        List<RecordMetadata> updatedRecordsMetadata = new ArrayList();
        for (RecordMetadata recordMetadata : recordsMetadata) {
            List<String> gcsVersionPathsWithoutLatestVersion = new ArrayList<>(recordMetadata.getGcsVersionPaths());
            gcsVersionPathsWithoutLatestVersion.remove(recordMetadata.getVersionPath(recordMetadata.getLatestVersion()));
            recordMetadata.setGcsVersionPaths(gcsVersionPathsWithoutLatestVersion);
            updatedRecordsMetadata.add(recordMetadata);
        }
        if (!updatedRecordsMetadata.isEmpty()) {
            this.commitDatastoreTransaction(updatedRecordsMetadata, collaborationContext);
        }
    }

    private void commitCloudStorageTransaction(List<RecordProcessing> recordsProcessing) {
        this.cloudStorage.write(recordsProcessing.toArray(new RecordProcessing[recordsProcessing.size()]));
    }

    private void commitDatastoreTransaction(List<RecordMetadata> recordsMetadata, Optional<CollaborationContext> collaborationContext) {
        try {
            this.recordRepository.createOrUpdate(recordsMetadata, collaborationContext);
        } catch (AppException e) {
            if (e.getError().getCode() == HttpStatus.SC_REQUEST_TOO_LONG) {
                throw new AppException(HttpStatus.SC_REQUEST_TOO_LONG, e.getError().getReason(), e.getError().getMessage());
            }
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error writing record.",
                    "The server could not process your request at the moment.", e);
        }
    }

    private Map<String, String> commitPatchDatastoreTransaction(Map<RecordMetadata, JsonPatch> jsonPatchPerRecord, Optional<CollaborationContext> collaborationContext) {
        try {
            return this.recordRepository.patch(jsonPatchPerRecord, collaborationContext);
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error patching records.",
                    "The server could not process your request at the moment.", e);
        }
    }
}
