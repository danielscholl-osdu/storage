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

import java.util.*;

import java.util.ArrayList;
import java.util.List;

import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.apache.http.HttpStatus;

import org.opengroup.osdu.core.common.storage.IPersistenceService;
import org.opengroup.osdu.storage.util.PatchOperationApplicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;

import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;

import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.response.BulkUpdateRecordsResponse;

import static java.util.Collections.singletonList;

@Service
public class RecordServiceImpl implements RecordService {

    @Autowired
    private IRecordsMetadataRepository recordRepository;

    @Autowired
    private ICloudStorage cloudStorage;

    @Autowired
    private IMessageBus pubSubClient;

    @Autowired
    private IEntitlementsAndCacheService entitlementsAndCacheService;

    @Autowired
    private ILegalService legalService;

    @Autowired
    private IPersistenceService persistenceService;

    @Autowired
    private TenantInfo tenant;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private StorageAuditLogger auditLogger;

    @Autowired
    private DataAuthorizationService dataAuthorizationService;

    private PatchOperationApplicator patchOperationApplicator = new PatchOperationApplicator();

    @Override
    public void purgeRecord(String recordId) {

        RecordMetadata recordMetadata = this.getRecordMetadata(recordId, true);
        boolean hasOwnerAccess = this.dataAuthorizationService.validateOwnerAccess(recordMetadata, OperationType.purge);

        if (!hasOwnerAccess) {
            this.auditLogger.purgeRecordFail(singletonList(recordId));
            throw new AppException(HttpStatus.SC_FORBIDDEN, "Access denied",
                    "The user is not authorized to purge the record");
        }

        try {
            this.recordRepository.delete(recordId);
        } catch (AppException e) {
            this.auditLogger.purgeRecordFail(singletonList(recordId));
            throw e;
        }

        try {
            this.cloudStorage.delete(recordMetadata);
        } catch (AppException e) {
            if (e.getError().getCode() != HttpStatus.SC_NOT_FOUND) {
                this.recordRepository.createOrUpdate(Lists.newArrayList(recordMetadata));
            }
            this.auditLogger.purgeRecordFail(singletonList(recordId));
            throw e;
        }

        this.auditLogger.purgeRecordSuccess(singletonList(recordId));
        this.pubSubClient.publishMessage(this.headers,
                new PubSubInfo(recordId, recordMetadata.getKind(), OperationType.delete));
    }

    @Override
    public void deleteRecord(String recordId, String user) {

        RecordMetadata recordMetadata = this.getRecordMetadata(recordId, false);

        this.validateDeleteAllowed(recordMetadata);

        recordMetadata.setStatus(RecordState.deleted);
        recordMetadata.setModifyTime(System.currentTimeMillis());
        recordMetadata.setModifyUser(user);

        List<RecordMetadata> recordsMetadata = new ArrayList<>();
        recordsMetadata.add(recordMetadata);

        this.recordRepository.createOrUpdate(recordsMetadata);
        this.auditLogger.deleteRecordSuccess(singletonList(recordId));

        PubSubInfo pubSubInfo = new PubSubInfo(recordId, recordMetadata.getKind(), OperationType.delete);
        this.pubSubClient.publishMessage(this.headers, pubSubInfo);
    }

    @Override
    public BulkUpdateRecordsResponse bulkUpdateRecords(RecordBulkUpdateParam recordBulkUpdateParam, String user) {
        RecordQuery bulkUpdateQuery = recordBulkUpdateParam.getQuery();
        List<PatchOperation> bulkUpdateOps = recordBulkUpdateParam.getOps();

        List<String> ids = bulkUpdateQuery.getIds();

        // validate record ids and properties
        this.validateRecordIds(ids);
        this.validateDuplicates(bulkUpdateOps);
        this.validateAcls(bulkUpdateOps);

        //<idWithoutVersion, idWithVersion>
        Map<String, String> idMap = this.mapRecordsAndVersions(ids);
        List<String> idsWithoutVersion = new ArrayList<>(idMap.keySet());
        Map<String, RecordMetadata> existingRecords = this.recordRepository.get(idsWithoutVersion);

        List<String> notFoundRecordIds = new ArrayList<>();
        List<String> unauthorizedRecordIds = this.dataAuthorizationService.validateUserAccessAndComplianceConstraints(
                this::validateLegalTags, this::validateOwnerAccess, bulkUpdateOps, idMap, existingRecords);

        // validate record access; 404 and 401
        List<RecordMetadata> validRecordsMetadata = new ArrayList<>();
        List<String> validRecordsId = new ArrayList<>();

        final long currentTimestamp = System.currentTimeMillis();
        for (String id : idsWithoutVersion) {
            String idWithVersion = idMap.get(id);
            RecordMetadata metadata = existingRecords.get(id);

            if (unauthorizedRecordIds.contains(idWithVersion)) {
                ids.remove(idWithVersion);
                this.auditLogger.createOrUpdateRecordsFail(singletonList(idWithVersion));
            }

            if (metadata == null) {
                notFoundRecordIds.add(idWithVersion);
                ids.remove(idWithVersion);
            } else {
                metadata = this.patchOperationApplicator.updateMetadataWithOperations(metadata, bulkUpdateOps);
                metadata.setModifyUser(user);
                metadata.setModifyTime(currentTimestamp);
                validRecordsMetadata.add(metadata);
                validRecordsId.add(id);
            }
        }

        List<String> lockedRecordsId = this.persistenceService.updateMetadata(validRecordsMetadata, validRecordsId, idMap);
        for (String lockedId : lockedRecordsId) {
            ids.remove(lockedId);
        }

        return BulkUpdateRecordsResponse.builder()
                .notFoundRecordIds(notFoundRecordIds)
                .unAuthorizedRecordIds(unauthorizedRecordIds)
                .recordIds(ids)
                .lockedRecordIds(lockedRecordsId)
                .recordCount(ids.size()).build();
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

    private RecordMetadata getRecordMetadata(String recordId, boolean isPurgeRequest) {

        String tenantName = tenant.getName();
        if (!Record.isRecordIdValidFormatAndTenant(recordId, tenantName)) {
            String msg = String.format("The record '%s' does not belong to account '%s'", recordId, tenantName);

            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid record ID", msg);
        }

        RecordMetadata record = this.recordRepository.get(recordId);
        String msg = String.format("Record with id '%s' does not exist", recordId);
        if ((record == null || record.getStatus() != RecordState.active) && !isPurgeRequest) {
            throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found", msg);
        }
        if (record == null && isPurgeRequest) {
            throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found", msg);
        }

        return record;
    }

    private Map<String, String> mapRecordsAndVersions(List<String> recordIds) {
        Map<String, String> idMap = new HashMap<>();
        for (String id : recordIds) {
            String[] idParts = id.split(":");
            if (idParts.length == 4) {
                String idWithoutVersion = id.substring(0, id.length() - idParts[3].length() - 1);
                idMap.put(idWithoutVersion, id);
            } else {
                idMap.put(id, id);
            }
        }
        return idMap;
    }

    private void validateDeleteAllowed(RecordMetadata recordMetadata) {
        if (!this.dataAuthorizationService.hasAccess(recordMetadata, OperationType.delete)) {
            this.auditLogger.deleteRecordFail(singletonList(recordMetadata.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, "Access denied", "The user is not authorized to perform this action");
        }
    }

    private void validateDuplicates(List<PatchOperation> ops) {
        Set<String> paths = new HashSet<>();
        for (PatchOperation op : ops) {
            String path = op.getPath();
            if (paths.contains(path)) {
                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Duplicate paths", "Users can only update a path once per request.");
            }
            paths.add(path);
        }
    }

    private void validateAcls(List<PatchOperation> ops) {
        for (PatchOperation op : ops) {
            Set<String> valueSet = new HashSet<>(Arrays.asList(op.getValue()));
            String path = op.getPath();
            if (path.startsWith("/acl")) {
                if (!this.entitlementsAndCacheService.isValidAcl(this.headers, valueSet)) {
                    throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid ACLs", "Invalid ACLs provided in acl path.");
                }
            }
        }
    }

    private void validateLegalTags(List<PatchOperation> ops) {
        for (PatchOperation op : ops) {
            String path = op.getPath();
            Set<String> valueSet = new HashSet<>(Arrays.asList(op.getValue()));
            if (path.startsWith("/legal")) {
                this.legalService.validateLegalTags(valueSet);
            }
        }
    }

//    private void validateOps(List<PatchOperation> ops) {
//        Set<String> paths = new HashSet<>();
//        for (PatchOperation op : ops) {
//            String path = op.getPath();
//            if (paths.contains(path)) {
//                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Duplicate paths", "Users can only update a path once per request.");
//            }
//            paths.add(path);
//
//            Set<String> valueSet = new HashSet<>(Arrays.asList(op.getValue()));
//            if (path.startsWith("/acl")) {
//                if (!this.entitlementsAndCacheService.isValidAcl(this.headers, valueSet)) {
//                    throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid ACLs", "Invalid ACLs provided in acl path.");
//                }
//            } else {
//                this.legalService.validateLegalTags(valueSet);
//            }
//        }
//    }

    private void validateRecordIds(List<String> recordIds) {
        for (String id : recordIds) {
            if (!Record.isRecordIdValidFormatAndTenant(id, this.tenant.getName())) {
                String msg = String.format(
                        "The record '%s' does not follow the naming convention: the first id component must be '%s'",
                        id, this.tenant.getName());
                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid record id", msg);
            }
        }
    }
}