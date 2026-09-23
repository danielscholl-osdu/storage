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

import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.util.Role;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Enum mapping each audit operation to the entitlements groups authorized to perform it.
 * This is the single source of truth for the {@code requiredGroupsForAction} field in
 * {@link org.opengroup.osdu.core.common.logging.audit.AuditPayload}.
 */
public enum AuditOperation {

    CREATE_OR_UPDATE_RECORDS(Arrays.asList(StorageRole.CREATOR, StorageRole.ADMIN)),
    DELETE_RECORD(Arrays.asList(StorageRole.CREATOR, StorageRole.ADMIN)),
    PURGE_RECORD(Collections.singletonList(StorageRole.ADMIN)),
    READ_ALL_VERSIONS_OF_RECORD(Arrays.asList(StorageRole.VIEWER, StorageRole.CREATOR, StorageRole.ADMIN)),
    READ_SPECIFIC_VERSION_OF_RECORD(Arrays.asList(StorageRole.VIEWER, StorageRole.CREATOR, StorageRole.ADMIN)),
    READ_LATEST_VERSION_OF_RECORD(Arrays.asList(StorageRole.VIEWER, StorageRole.CREATOR, StorageRole.ADMIN)),
    READ_MULTIPLE_RECORDS(Arrays.asList(StorageRole.VIEWER, StorageRole.CREATOR, StorageRole.ADMIN)),
    READ_ALL_KINDS(Arrays.asList(StorageRole.CREATOR, StorageRole.ADMIN)),
    READ_ALL_RECORDS_FROM_KIND(Collections.singletonList(StorageRole.ADMIN)),
    CREATE_SCHEMA(Arrays.asList(StorageRole.CREATOR, StorageRole.ADMIN)),
    DELETE_SCHEMA(Collections.singletonList(StorageRole.ADMIN)),
    READ_SCHEMA(Arrays.asList(StorageRole.VIEWER, StorageRole.CREATOR, StorageRole.ADMIN)),
    UPDATE_RECORD_COMPLIANCE_STATE(Arrays.asList(StorageRole.ADMIN, StorageRole.PUBSUB)),
    READ_MULTIPLE_RECORDS_WITH_CONVERSION(Arrays.asList(StorageRole.VIEWER, StorageRole.CREATOR, StorageRole.ADMIN)),
    PURGE_RECORD_VERSIONS(Collections.singletonList(StorageRole.ADMIN)),
    CREATE_REPLAY_REQUEST(Collections.singletonList(Role.USER_OPS));

    private final List<String> requiredGroups;

    AuditOperation(List<String> requiredGroups) {
        this.requiredGroups = Collections.unmodifiableList(requiredGroups);
    }

    public List<String> getRequiredGroups() {
        return requiredGroups;
    }
}
