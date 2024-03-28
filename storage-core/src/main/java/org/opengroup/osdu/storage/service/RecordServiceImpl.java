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

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.DeletionType;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PubSubDeleteInfo;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.exception.DeleteRecordsException;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.RecordChangedV2Delete;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.util.CollaborationUtil;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.opengroup.osdu.storage.util.RecordConstants.COLLABORATIONS_FEATURE_NAME;

@Service
public class RecordServiceImpl implements RecordService {

    public static final String ACCESS_DENIED = "Access denied";
    @Autowired
    private IRecordsMetadataRepository recordRepository;

    @Autowired
    private ICloudStorage cloudStorage;

    @Autowired
    private IMessageBus pubSubClient;

    @Autowired
    private TenantInfo tenant;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private StorageAuditLogger auditLogger;

    @Autowired
    private DataAuthorizationService dataAuthorizationService;

    @Autowired
    private RecordUtil recordUtil;
    @Autowired
    private IFeatureFlag collaborationFeatureFlag;

    @Override
    public void purgeRecord(String recordId, Optional<CollaborationContext> collaborationContext) {

        RecordMetadata recordMetadata = this.getRecordMetadata(recordId, true, collaborationContext);
        boolean hasOwnerAccess = this.dataAuthorizationService.validateOwnerAccess(recordMetadata, OperationType.purge);

        if (!hasOwnerAccess) {
            this.auditLogger.purgeRecordFail(singletonList(recordId));
            throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED,
                    "The user is not authorized to purge the record");
        }

        try {
            this.recordRepository.delete(recordId, collaborationContext);
        } catch (AppException e) {
            this.auditLogger.purgeRecordFail(singletonList(recordId));
            throw e;
        }

        try {
            this.cloudStorage.delete(recordMetadata);
        } catch (AppException e) {
            if (e.getError().getCode() != HttpStatus.SC_NOT_FOUND) {
                this.recordRepository.createOrUpdate(Lists.newArrayList(recordMetadata), collaborationContext);
            }
            this.auditLogger.purgeRecordFail(singletonList(recordId));
            throw e;
        }

        this.auditLogger.purgeRecordSuccess(singletonList(recordId));
        if (collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)) {
            this.pubSubClient.publishMessage(collaborationContext, this.headers, getRecordChangedV2Delete(recordId, recordMetadata, DeletionType.hard));
        }
        if (!collaborationContext.isPresent()) {
            this.pubSubClient.publishMessage(this.headers, new PubSubDeleteInfo(recordId, recordMetadata.getKind(), DeletionType.hard));
        }
    }

    @Override
    public void purgeRecordVersions(String recordId, Integer limit, String user, Optional<CollaborationContext> collaborationContext) {
        RecordMetadata recordMetadata = getRecordMetadata(recordId, true, collaborationContext);
        List<String> currentRecordVersionPaths = recordMetadata.getGcsVersionPaths();

        validateOwnerAccess(recordMetadata);
        validateVersionPathCount(currentRecordVersionPaths, recordId, limit);

        Pair<List<String>, List<String>> recordVersionPathsToRetainDeletePair = deleteVersionsPathsByLimit(currentRecordVersionPaths, limit);
        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        recordMetadata.setGcsVersionPaths(recordVersionPathsToRetainDeletePair.getLeft());
        recordMetadata.setModifyTime(System.currentTimeMillis());
        recordMetadata.setModifyUser(user);
        recordMetadataList.add(recordMetadata);

        try {
            this.recordRepository.createOrUpdate(recordMetadataList, collaborationContext);
        } catch (AppException appException) {
            this.auditLogger.purgeRecordVersionsFail(recordId, recordVersionPathsToRetainDeletePair.getRight());
            throw appException;
        }

        try {
            this.cloudStorage.deleteVersions(recordVersionPathsToRetainDeletePair.getRight());
        } catch (AppException appException) {
            recordMetadata.setGcsVersionPaths(currentRecordVersionPaths);
            this.recordRepository.createOrUpdate(recordMetadataList, collaborationContext);
            this.auditLogger.purgeRecordVersionsFail(recordId, recordVersionPathsToRetainDeletePair.getRight());
            throw appException;
        }

        this.auditLogger.purgeRecordVersionsSuccess(recordId, recordVersionPathsToRetainDeletePair.getRight());
    }

    private void validateVersionPathCount(List<String> currentRecordVersionPaths, String recordId, Integer limit) {
        if (currentRecordVersionPaths.size() == 1) {
            String message = String.format("The record '%s' has only one version", recordId);
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "No Record versions to purge", message);
        }
        if (currentRecordVersionPaths.size() - 1 < limit) {
            String message = String.format("The record '%s' version count (excluding latest version) is : %d , which is less than limit value : %d ", recordId, currentRecordVersionPaths.size() - 1, limit);
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid limit", message);
        }
    }

    private void validateOwnerAccess(RecordMetadata recordMetadata) {
        boolean hasOwnerAccess = dataAuthorizationService.validateOwnerAccess(recordMetadata, OperationType.purge);
        if (!hasOwnerAccess) {
            auditLogger.purgeRecordVersionsFail(recordMetadata.getId(), singletonList(recordMetadata.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED,
                    "The user is not authorized to purge the record versions");
        }
    }

    private Pair<List<String>, List<String>> deleteVersionsPathsByLimit(List<String> currentRecordVersionPaths, Integer limit) {
        List<String> recordVersionPathsToRetain = new ArrayList<>();
        List<String> recordVersionPathsToDelete = new ArrayList<>();
        for (int i = 0; i < currentRecordVersionPaths.size(); i++) {
            String currentRecordVersionPath = currentRecordVersionPaths.get(i);
            if (i < limit)
                recordVersionPathsToDelete.add(currentRecordVersionPath);
            else
                recordVersionPathsToRetain.add(currentRecordVersionPath);
        }
        return new ImmutablePair<>(recordVersionPathsToRetain, recordVersionPathsToDelete);
    }

    @Override
    public void deleteRecord(String recordId, String user, Optional<CollaborationContext> collaborationContext) {

        RecordMetadata recordMetadata = this.getRecordMetadata(recordId, false, collaborationContext);

        this.validateDeleteAllowed(recordMetadata);

        recordMetadata.setStatus(RecordState.deleted);
        recordMetadata.setModifyTime(System.currentTimeMillis());
        recordMetadata.setModifyUser(user);

        List<RecordMetadata> recordsMetadata = new ArrayList<>();
        recordsMetadata.add(recordMetadata);

        this.recordRepository.createOrUpdate(recordsMetadata, collaborationContext);
        this.auditLogger.deleteRecordSuccess(singletonList(recordId));

        if (collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)) {
            RecordChangedV2Delete recordChangedV2Delete = getRecordChangedV2Delete(recordId, recordMetadata, DeletionType.soft);
            this.pubSubClient.publishMessage(collaborationContext, this.headers, recordChangedV2Delete);
        }
        if (!collaborationContext.isPresent()) {
            PubSubDeleteInfo pubSubDeleteInfo = new PubSubDeleteInfo(recordId, recordMetadata.getKind(), DeletionType.soft);
            this.pubSubClient.publishMessage(this.headers, pubSubDeleteInfo);
        }
    }

    @Override
    public void bulkDeleteRecords(List<String> records, String user, Optional<CollaborationContext> collaborationContext) {
        recordUtil.validateRecordIds(records);
        List<Pair<String, String>> notDeletedRecords = new ArrayList<>();

        List<RecordMetadata> recordsMetadata = getRecordsMetadata(records, notDeletedRecords, collaborationContext);
        this.validateAccess(recordsMetadata, notDeletedRecords);

        Date modifyTime = new Date();
        recordsMetadata.forEach(recordMetadata -> {
                    recordMetadata.setStatus(RecordState.deleted);
                    recordMetadata.setModifyTime(modifyTime.getTime());
                    recordMetadata.setModifyUser(user);
                }
        );
        if (notDeletedRecords.isEmpty()) {
            this.recordRepository.createOrUpdate(recordsMetadata, collaborationContext);
            this.auditLogger.deleteRecordSuccess(records);
            publishDeletedRecords(collaborationContext, recordsMetadata);
        } else {
            List<String> deletedRecords = new ArrayList<>(records);
            List<String> notDeletedRecordIds = notDeletedRecords.stream()
                    .map(Pair::getKey)
                    .collect(toList());
            deletedRecords.removeAll(notDeletedRecordIds);
            if (!deletedRecords.isEmpty()) {
                this.recordRepository.createOrUpdate(recordsMetadata, collaborationContext);
                this.auditLogger.deleteRecordSuccess(deletedRecords);
                publishDeletedRecords(collaborationContext, recordsMetadata);
            }
            throw new DeleteRecordsException(notDeletedRecords);
        }
    }

    private RecordChangedV2Delete getRecordChangedV2Delete(String recordId, RecordMetadata recordMetadata, DeletionType deletionType) {
        return RecordChangedV2Delete.builder()
                .id(recordId)
                .version(recordMetadata.getLatestVersion())
                .modifiedBy(recordMetadata.getModifyUser())
                .kind(recordMetadata.getKind())
                .op(OperationType.delete)
                .deletionType(deletionType)
                .build();
    }

    private void publishDeletedRecords(Optional<CollaborationContext> collaborationContext, List<RecordMetadata> records) {

        if (collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)) {
            List<RecordChangedV2Delete> messages = records.stream()
                    .map(recordMetadata -> getRecordChangedV2Delete(recordMetadata.getId(), recordMetadata, DeletionType.soft))
                    .collect(Collectors.toList());
            pubSubClient.publishMessage(collaborationContext, headers, messages.toArray(new RecordChangedV2Delete[messages.size()]));
        }
        if (!collaborationContext.isPresent()) {
            List<PubSubDeleteInfo> messages = records.stream()
                    .map(recordMetadata -> new PubSubDeleteInfo(recordMetadata.getId(), recordMetadata.getKind(), DeletionType.soft))
                    .collect(Collectors.toList());
            pubSubClient.publishMessage(headers, messages.toArray(new PubSubDeleteInfo[messages.size()]));
        }
    }

    protected RecordMetadata getRecordMetadata(String recordId, boolean isPurgeRequest, Optional<CollaborationContext> collaborationContext) {

        String tenantName = tenant.getName();
        if (!Record.isRecordIdValidFormatAndTenant(recordId, tenantName)) {
            String msg = String.format("The record '%s' does not belong to account '%s'", recordId, tenantName);
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid record ID", msg);
        }

        RecordMetadata recordMetadata = this.recordRepository.get(recordId, collaborationContext);
        String msg = String.format("Record with id '%s' does not exist", recordId);
        if ((recordMetadata == null || recordMetadata.getStatus() != RecordState.active) && !isPurgeRequest) {
            throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found", msg);
        }
        if (recordMetadata == null && isPurgeRequest) {
            throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found", msg);
        }

        return recordMetadata;
    }

    private List<RecordMetadata> getRecordsMetadata(List<String> recordIds, List<Pair<String, String>> notDeletedRecords, Optional<CollaborationContext> collaborationContext) {
        Map<String, RecordMetadata> result = this.recordRepository.get(recordIds, collaborationContext);

        recordIds.stream()
                .filter(recordId -> result.get(CollaborationUtil.getIdWithNamespace(recordId, collaborationContext)) == null)
                .forEach(recordId -> {
                    String msg = String.format("Record with id '%s' not found", recordId);
                    notDeletedRecords.add(new ImmutablePair<>(recordId, msg));
                    auditLogger.deleteRecordFail(singletonList(msg));
                });

        return result.entrySet().stream().map(Map.Entry::getValue).collect(toList());
    }

    private void validateDeleteAllowed(RecordMetadata recordMetadata) {
        if (!this.dataAuthorizationService.validateOwnerAccess(recordMetadata, OperationType.delete)) {
            this.auditLogger.deleteRecordFail(singletonList(recordMetadata.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED, "The user is not authorized to perform this action");
        }
    }

    private void validateAccess(List<RecordMetadata> recordsMetadata, List<Pair<String, String>> notDeletedRecords) {
        new ArrayList<>(recordsMetadata).forEach(recordMetadata -> {
            if (!this.dataAuthorizationService.validateOwnerAccess(recordMetadata, OperationType.delete)) {
                String msg = String
                        .format("The user is not authorized to perform delete record with id %s", recordMetadata.getId());
                this.auditLogger.deleteRecordFail(singletonList(msg));
                notDeletedRecords.add(new ImmutablePair<>(recordMetadata.getId(), msg));
                recordsMetadata.remove(recordMetadata);
            }
        });
    }
}
