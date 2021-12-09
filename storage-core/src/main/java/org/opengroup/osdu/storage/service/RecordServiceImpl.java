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
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.exception.DeleteRecordsException;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@Service
public class RecordServiceImpl implements RecordService {

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
    public void bulkDeleteRecords(List<String> records, String user) {
        recordUtil.validateRecordIds(records);
        List<Pair<String, String>> notDeletedRecords = new ArrayList<>();

        List<RecordMetadata> recordsMetadata = getRecordsMetadata(records, notDeletedRecords);
        this.validateAccess(recordsMetadata, notDeletedRecords);

        Date modifyTime = new Date();
        recordsMetadata.forEach(recordMetadata -> {
                recordMetadata.setStatus(RecordState.deleted);
                recordMetadata.setModifyTime(modifyTime.getTime());
                recordMetadata.setModifyUser(user);
            }
        );
        if (notDeletedRecords.isEmpty()) {
            this.recordRepository.createOrUpdate(recordsMetadata);
            this.auditLogger.deleteRecordSuccess(records);
            publishDeletedRecords(recordsMetadata);
        } else {
            List<String> deletedRecords = new ArrayList<>(records);
            List<String> notDeletedRecordIds = notDeletedRecords.stream()
                .map(Pair::getKey)
                .collect(toList());
            deletedRecords.removeAll(notDeletedRecordIds);
            if(!deletedRecords.isEmpty()) {
                this.recordRepository.createOrUpdate(recordsMetadata);
                this.auditLogger.deleteRecordSuccess(deletedRecords);
                publishDeletedRecords(recordsMetadata);
            }
            throw new DeleteRecordsException(notDeletedRecords);
        }
    }

    private void publishDeletedRecords(List<RecordMetadata> records) {
        List<PubSubInfo> messages = records.stream()
            .map(recordMetadata -> new PubSubInfo(recordMetadata.getId(), recordMetadata.getKind(), OperationType.delete))
            .collect(Collectors.toList());
        pubSubClient.publishMessage(headers, messages.toArray(new PubSubInfo[messages.size()]));
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

    private List<RecordMetadata> getRecordsMetadata(List<String> recordIds, List<Pair<String, String>> notDeletedRecords) {
        Map<String, RecordMetadata> result = this.recordRepository.get(recordIds);

        recordIds.stream()
            .filter(recordId -> result.get(recordId) == null)
            .forEach(recordId -> {
                String msg = String.format("Record with id '%s' not found", recordId);
                notDeletedRecords.add(new ImmutablePair<>(recordId, msg));
                auditLogger.deleteRecordFail(singletonList(msg));
            });

        return result.entrySet().stream().map(Map.Entry::getValue).collect(toList());
    }

    private void validateDeleteAllowed(RecordMetadata recordMetadata) {
        if (!this.dataAuthorizationService.hasAccess(recordMetadata, OperationType.delete)) {
            this.auditLogger.deleteRecordFail(singletonList(recordMetadata.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, "Access denied", "The user is not authorized to perform this action");
        }
    }

    private void validateAccess(List<RecordMetadata> recordsMetadata, List<Pair<String, String>> notDeletedRecords) {
        new ArrayList<>(recordsMetadata).forEach(recordMetadata -> {
            if (!this.dataAuthorizationService.hasAccess(recordMetadata, OperationType.delete)) {
                String msg = String
                    .format("The user is not authorized to perform delete record with id %s", recordMetadata.getId());
                this.auditLogger.deleteRecordFail(singletonList(msg));
                notDeletedRecords.add(new ImmutablePair<>(recordMetadata.getId(), msg));
                recordsMetadata.remove(recordMetadata);
            }
        });
    }
}