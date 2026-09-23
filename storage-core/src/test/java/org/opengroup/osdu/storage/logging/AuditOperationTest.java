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

import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.util.Role;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuditOperationTest {

    @Test
    public void testEnumCount() {
        assertEquals(16, AuditOperation.values().length);
    }

    @Test
    public void testCreateOrUpdateRecordsRoles() {
        List<String> roles = AuditOperation.CREATE_OR_UPDATE_RECORDS.getRequiredGroups();
        assertEquals(2, roles.size());
        assertTrue(roles.contains(StorageRole.CREATOR));
        assertTrue(roles.contains(StorageRole.ADMIN));
    }

    @Test
    public void testPurgeRecordRoles() {
        List<String> roles = AuditOperation.PURGE_RECORD.getRequiredGroups();
        assertEquals(1, roles.size());
        assertTrue(roles.contains(StorageRole.ADMIN));
    }

    @Test
    public void testReadOperationRoles() {
        List<String> roles = AuditOperation.READ_MULTIPLE_RECORDS.getRequiredGroups();
        assertEquals(3, roles.size());
        assertTrue(roles.contains(StorageRole.VIEWER));
        assertTrue(roles.contains(StorageRole.CREATOR));
        assertTrue(roles.contains(StorageRole.ADMIN));
    }

    @Test
    public void testUpdateRecordComplianceStateRoles() {
        List<String> roles = AuditOperation.UPDATE_RECORD_COMPLIANCE_STATE.getRequiredGroups();
        assertEquals(2, roles.size());
        assertTrue(roles.contains(StorageRole.ADMIN));
        assertTrue(roles.contains(StorageRole.PUBSUB));
    }

    @Test
    public void testCreateReplayRequestRoles() {
        List<String> roles = AuditOperation.CREATE_REPLAY_REQUEST.getRequiredGroups();
        assertEquals(1, roles.size());
        assertTrue(roles.contains(Role.USER_OPS));
    }

    @Test
    public void testRolesAreImmutable() {
        List<String> roles = AuditOperation.CREATE_OR_UPDATE_RECORDS.getRequiredGroups();
        assertThrows(UnsupportedOperationException.class, () -> roles.add("new.role"));
    }

    @Test
    public void testAllOperationsHaveNonEmptyRoles() {
        for (AuditOperation op : AuditOperation.values()) {
            assertTrue(op.getRequiredGroups().size() > 0,
                    op.name() + " should have at least one required role");
        }
    }
}
