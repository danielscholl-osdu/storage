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

import java.util.ArrayList;
import java.util.List;

import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.apache.http.HttpStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;

import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;

import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;

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
    private TenantInfo tenant;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private StorageAuditLogger auditLogger;

    @Override
    public void purgeRecord(String recordId) {

        RecordMetadata recordMetadata = this.getRecordMetadata(recordId, true);
        boolean hasOwnerAccess = this.entitlementsAndCacheService.hasOwnerAccess(this.headers, recordMetadata.getAcl().getOwners());

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

        this.validateAccess(recordMetadata);

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

    private void validateAccess(RecordMetadata recordMetadata) {
        if (!this.cloudStorage.hasAccess(recordMetadata)) {
            this.auditLogger.deleteRecordFail(singletonList(recordMetadata.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, "Access denied",
                    "The user is not authorized to perform this action");
        }
    }
}