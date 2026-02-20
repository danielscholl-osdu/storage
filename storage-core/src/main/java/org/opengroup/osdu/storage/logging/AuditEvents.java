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
import org.opengroup.osdu.core.common.logging.audit.AuditAction;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.logging.audit.AuditStatus;

import java.util.List;

import static java.util.Collections.singletonList;

public class AuditEvents {

    private static final String UNKNOWN = "unknown";
    private static final String UNKNOWN_IP = "0.0.0.0";

    private static final String CREATE_OR_UPDATE_RECORD_ACTION_ID = "ST001";
    private static final String CREATE_OR_UPDATE_RECORD_MESSAGE = "Records created or updated";
    private static final String DELETE_RECORD_ACTION_ID = "ST002";
    private static final String DELETE_RECORD_MESSAGE = "Record deleted";
    private static final String PURGE_RECORD_ACTION_ID = "ST003";
    private static final String PURGE_RECORD_MESSAGE = "Record purged";
    private static final String READ_ALL_VERSIONS_OF_RECORD_ACTION_ID = "ST004";
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

    private final String user;
    private final String userIpAddress;
    private final String userAgent;
    private final String userAuthorizedGroupName;

    public AuditEvents(String user, String userIpAddress, String userAgent, String userAuthorizedGroupName) {
        this.user = Strings.isNullOrEmpty(user) ? UNKNOWN : user;
        this.userIpAddress = Strings.isNullOrEmpty(userIpAddress) ? UNKNOWN_IP : userIpAddress;
        this.userAgent = Strings.isNullOrEmpty(userAgent) ? UNKNOWN : userAgent;
        this.userAuthorizedGroupName = Strings.isNullOrEmpty(userAuthorizedGroupName) ? UNKNOWN : userAuthorizedGroupName;
    }

    private AuditPayload.AuditPayloadBuilder createAuditPayloadBuilder(List<String> requiredGroupsForAction, String actionId) {
        return AuditPayload.builder()
            .user(this.user)
            .actionId(actionId)
            .requiredGroupsForAction(requiredGroupsForAction)
            .userIpAddress(this.userIpAddress)
            .userAgent(this.userAgent)
            .userAuthorizedGroupName(this.userAuthorizedGroupName);
    }

    public AuditPayload getReadMultipleRecordsSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.READ_MULTIPLE_RECORDS.getRequiredGroups(), READ_MULTIPLE_RECORDS_ACTION_ID)
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .message(READ_MULTIPLE_RECORDS_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getReadAllRecordsOfGivenKindSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.READ_ALL_RECORDS_FROM_KIND.getRequiredGroups(), READ_ALL_RECORDS_FROM_KIND_ACTION_ID)
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .message(READ_ALL_RECORDS_FROM_KIND_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getReadAllVersionsOfRecordSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.READ_ALL_VERSIONS_OF_RECORD.getRequiredGroups(), READ_ALL_VERSIONS_OF_RECORD_ACTION_ID)
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .message(READ_ALL_VERSIONS_OF_RECORD_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getReadAllVersionsOfRecordFail(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.READ_ALL_VERSIONS_OF_RECORD.getRequiredGroups(), READ_ALL_VERSIONS_OF_RECORD_ACTION_ID)
                .action(AuditAction.READ)
                .status(AuditStatus.FAILURE)
                .message(READ_ALL_VERSIONS_OF_RECORD_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getReadSpecificVersionOfRecordSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.READ_SPECIFIC_VERSION_OF_RECORD.getRequiredGroups(), READ_RECORD_SPECIFIC_VERSION_ACTION_ID)
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .message(READ_RECORD_SPECIFIC_VERSION_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getReadSpecificVersionOfRecordFail(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.READ_SPECIFIC_VERSION_OF_RECORD.getRequiredGroups(), READ_RECORD_SPECIFIC_VERSION_ACTION_ID)
                .action(AuditAction.READ)
                .status(AuditStatus.FAILURE)
                .message(READ_RECORD_SPECIFIC_VERSION_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getReadLatestVersionOfRecordSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.READ_LATEST_VERSION_OF_RECORD.getRequiredGroups(), READ_RECORD_LATEST_VERSION_ACTION_ID)
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .message(READ_RECORD_LATEST_VERSION_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getReadLatestVersionOfRecordFail(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.READ_LATEST_VERSION_OF_RECORD.getRequiredGroups(), READ_RECORD_LATEST_VERSION_ACTION_ID)
                .action(AuditAction.READ)
                .status(AuditStatus.FAILURE)
                .message(READ_RECORD_LATEST_VERSION_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getCreateOrUpdateRecordsEventSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.CREATE_OR_UPDATE_RECORDS.getRequiredGroups(), CREATE_OR_UPDATE_RECORD_ACTION_ID)
                .action(AuditAction.UPDATE)
                .status(AuditStatus.SUCCESS)
                .message(CREATE_OR_UPDATE_RECORD_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getCreateOrUpdateRecordsEventFail(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.CREATE_OR_UPDATE_RECORDS.getRequiredGroups(), CREATE_OR_UPDATE_RECORD_ACTION_ID)
                .action(AuditAction.UPDATE)
                .status(AuditStatus.FAILURE)
                .message(CREATE_OR_UPDATE_RECORD_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getDeleteRecordEventSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.DELETE_RECORD.getRequiredGroups(), DELETE_RECORD_ACTION_ID)
                .action(AuditAction.DELETE)
                .status(AuditStatus.SUCCESS)
                .message(DELETE_RECORD_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getDeleteRecordEventFail(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.DELETE_RECORD.getRequiredGroups(), DELETE_RECORD_ACTION_ID)
                .action(AuditAction.DELETE)
                .status(AuditStatus.FAILURE)
                .message(DELETE_RECORD_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getPurgeRecordEventSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.PURGE_RECORD.getRequiredGroups(), PURGE_RECORD_ACTION_ID)
                .action(AuditAction.DELETE)
                .status(AuditStatus.SUCCESS)
                .message(PURGE_RECORD_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getPurgeRecordEventFail(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.PURGE_RECORD.getRequiredGroups(), PURGE_RECORD_ACTION_ID)
                .action(AuditAction.DELETE)
                .status(AuditStatus.FAILURE)
                .message(PURGE_RECORD_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getAllKindsEventSuccess() {
        return createAuditPayloadBuilder(AuditOperation.READ_ALL_KINDS.getRequiredGroups(), READ_GET_ALL_KINDS_ACTION_ID)
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .message(READ_GET_ALL_KINDS_MESSAGE)
                .resources(singletonList("All Kinds"))
                .build();
    }

    public AuditPayload getCreateSchemaEventSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.CREATE_SCHEMA.getRequiredGroups(), CREATE_SCHEMA_ACTION_ID)
                .action(AuditAction.CREATE)
                .status(AuditStatus.SUCCESS)
                .message(CREATE_SCHEMA_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getDeleteSchemaEventSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.DELETE_SCHEMA.getRequiredGroups(), DELETE_SCHEMA_ACTION_ID)
                .action(AuditAction.DELETE)
                .status(AuditStatus.SUCCESS)
                .message(DELETE_SCHEMA_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getReadSchemaEventSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.READ_SCHEMA.getRequiredGroups(), READ_SCHEMA_ACTION_ID)
                .action(AuditAction.READ)
                .status(AuditStatus.SUCCESS)
                .message(READ_SCHEMA_MESSAGE)
                .resources(resources)
                .build();
    }

    public AuditPayload getUpdateRecordsComplianceStateEventSuccess(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.UPDATE_RECORD_COMPLIANCE_STATE.getRequiredGroups(), UPDATE_RECORD_COMPLIANCE_STATE_ACTION_ID)
                .action(AuditAction.UPDATE)
                .status(AuditStatus.SUCCESS)
                .message(UPDATE_RECORD_COMPLIANCE_STATE_MESSAGE)
                .resources(resources)
                .build();
    }
}
