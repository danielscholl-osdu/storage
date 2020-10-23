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

package org.opengroup.osdu.storage.logging;

import com.google.common.base.Strings;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.logging.audit.AuditStatus;
import org.opengroup.osdu.core.common.logging.audit.AuditAction;

import java.util.List;

import static java.util.Collections.singletonList;

public class StorageAuditEvents {
    private static final String CREATE_OR_UPDATE_RECORD_ACTION_ID = "ST001";
    private static final String CREATE_OR_UPDATE_RECORD_MESSAGE = "Records created or updated";
    private static final String DELETE_RECORD_ACTION_ID = "ST002";
    private static final String DELETE_RECORD_MESSAGE = "Record deleted";
    private static final String PURGE_RECORD_ACTION_ID = "ST003";
    private static final String PURGE_RECORD_MESSAGE = "Record purged";
    private static final String READ_ALL_VERSIONS_OF_RECORD_ACTION_ID ="ST004";
    private static final String READ_ALL_VERSIONS_OF_RECORD_MESSAGE = "Read all versions of record";
    private static final String READ_RECORD_SPECIFIC_VERSION_ACTION_ID = "ST005";
    private static final String READ_RECORD_SPECIFIC_VERSION_MESSAGE = "Read a specific version of record";
    private static final String READ_RECORD_LATEST_VERSION_ACTION_ID = "ST006";
    private static final String READ_RECORD_LATEST_VERSION_MESSAGE = "Read the latest version of record";

    private static final String READ_MULTIPLE_RECORDS_ACTION_ID = "ST007";
    private static final String READ_MULTIPLE_RECORDS_MESSAGE = "Read multiple records";
    private static final String READ_GET_ALL_KINDS_ACTION_ID = "ST008";
    private static final String READ_GET_ALL_KINDS_MESSAGE = "Read all kinds";
    private static final String READ_ALL_RECORDS_FROM_KIND_ACTION_ID = "ST009";
    private static final String READ_ALL_RECORDS_FROM_KIND_MESSAGE = "Read all record ids of the given kind";

    private static final String CREATE_SCHEMA_ACTION_ID = "ST010";
    private static final String CREATE_SCHEMA_MESSAGE = "Schema created";
    private static final String DELETE_SCHEMA_ACTION_ID = "ST011";
    private static final String DELETE_SCHEMA_MESSAGE = "Schema deleted";
    private static final String READ_SCHEMA_ACTION_ID = "ST012";
    private static final String READ_SCHEMA_MESSAGE = "Schema read";
    private static final String UPDATE_RECORD_COMPLIANCE_STATE_ACTION_ID = "ST013";
    private static final String UPDATE_RECORD_COMPLIANCE_STATE_MESSAGE = "Record compliance state updated";

    private static final String READ_MULTIPLE_RECORDS_WITH_CONVERSION_ACTION_ID = "ST014";
    private static final String READ_MULTIPLE_RECORDS_WITH_CONVERSION_MESSAGE = "Read multiple records with optional conversion";

    private final String user;

    public StorageAuditEvents(String user) {
        if (Strings.isNullOrEmpty(user)) {
            throw new IllegalArgumentException("User not provided for audit events.");
        }
        this.user = user;
    }

    public AuditPayload getReadMultipleRecordsSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .actionId(READ_MULTIPLE_RECORDS_ACTION_ID)
                .message(READ_MULTIPLE_RECORDS_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getReadAllRecordsOfGivenKindSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .actionId(READ_ALL_RECORDS_FROM_KIND_ACTION_ID)
                .message(READ_ALL_RECORDS_FROM_KIND_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getReadAllVersionsOfRecordSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .actionId(READ_ALL_VERSIONS_OF_RECORD_ACTION_ID)
                .message(READ_ALL_VERSIONS_OF_RECORD_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getReadAllVersionsOfRecordFail(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.FAILURE)
                .actionId(READ_ALL_VERSIONS_OF_RECORD_ACTION_ID)
                .message(READ_ALL_VERSIONS_OF_RECORD_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getReadSpecificVersionOfRecordSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .actionId(READ_RECORD_SPECIFIC_VERSION_ACTION_ID)
                .message(READ_RECORD_SPECIFIC_VERSION_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getReadSpecificVersionOfRecordFail(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.FAILURE)
                .actionId(READ_RECORD_SPECIFIC_VERSION_ACTION_ID)
                .message(READ_RECORD_SPECIFIC_VERSION_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getReadLatestVersionOfRecordSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .actionId(READ_RECORD_LATEST_VERSION_ACTION_ID)
                .message(READ_RECORD_LATEST_VERSION_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getReadLatestVersionOfRecordFail(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.FAILURE)
                .actionId(READ_RECORD_LATEST_VERSION_ACTION_ID)
                .message(READ_RECORD_LATEST_VERSION_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }



    public AuditPayload getCreateOrUpdateRecordsEventSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.UPDATE)
                .status(AuditStatus.SUCCESS)
                .actionId(CREATE_OR_UPDATE_RECORD_ACTION_ID)
                .message(CREATE_OR_UPDATE_RECORD_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getCreateOrUpdateRecordsEventFail(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.UPDATE)
                .status(AuditStatus.FAILURE)
                .actionId(CREATE_OR_UPDATE_RECORD_ACTION_ID)
                .message(CREATE_OR_UPDATE_RECORD_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getDeleteRecordEventSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.DELETE)
                .status(AuditStatus.SUCCESS)
                .actionId(DELETE_RECORD_ACTION_ID)
                .message(DELETE_RECORD_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getDeleteRecordEventFail(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.DELETE)
                .status(AuditStatus.FAILURE)
                .actionId(DELETE_RECORD_ACTION_ID)
                .message(DELETE_RECORD_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getPurgeRecordEventSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.DELETE)
                .status(AuditStatus.SUCCESS)
                .actionId(PURGE_RECORD_ACTION_ID)
                .message(PURGE_RECORD_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getPurgeRecordEventFail(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.DELETE)
                .status(AuditStatus.FAILURE)
                .actionId(PURGE_RECORD_ACTION_ID)
                .message(PURGE_RECORD_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }


    public AuditPayload getAllKindsEventSuccess(List<String> resource) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .actionId(READ_GET_ALL_KINDS_ACTION_ID)
                .message(READ_GET_ALL_KINDS_MESSAGE)
                .resources(resource)
                .user(user)
                .build();
    }

    public AuditPayload getCreateSchemaEventSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.CREATE)
                .status(AuditStatus.SUCCESS)
                .actionId(CREATE_SCHEMA_ACTION_ID)
                .message(CREATE_SCHEMA_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getDeleteSchemaEventSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.DELETE)
                .status(AuditStatus.SUCCESS)
                .actionId(DELETE_SCHEMA_ACTION_ID)
                .message(DELETE_SCHEMA_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getReadSchemaEventSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.CREATE)
                .status(AuditStatus.SUCCESS)
                .actionId(READ_SCHEMA_ACTION_ID)
                .message(READ_SCHEMA_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getUpdateRecordsComplianceStateEventSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.UPDATE)
                .status(AuditStatus.SUCCESS)
                .actionId(UPDATE_RECORD_COMPLIANCE_STATE_ACTION_ID)
                .message(UPDATE_RECORD_COMPLIANCE_STATE_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getReadMultipleRecordsWithOptionalConversionSuccess(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .actionId(READ_MULTIPLE_RECORDS_WITH_CONVERSION_ACTION_ID)
                .message(READ_MULTIPLE_RECORDS_WITH_CONVERSION_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }

    public AuditPayload getReadMultipleRecordsWithOptionalConversionFail(List<String> resources) {
        return AuditPayload.builder()
                .action(AuditAction.READ)
                .status(AuditStatus.FAILURE)
                .actionId(READ_MULTIPLE_RECORDS_WITH_CONVERSION_ACTION_ID)
                .message(READ_MULTIPLE_RECORDS_WITH_CONVERSION_MESSAGE)
                .resources(resources)
                .user(user)
                .build();
    }
}
