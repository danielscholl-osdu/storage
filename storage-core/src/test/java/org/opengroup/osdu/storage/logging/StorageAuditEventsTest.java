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
import org.opengroup.osdu.core.common.logging.audit.AuditAction;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.logging.audit.AuditStatus;
import org.opengroup.osdu.core.common.model.storage.StorageRole;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StorageAuditEventsTest {

    private static final String USER = "test@example.com";
    private static final String IP = "10.0.0.1";
    private static final String AGENT = "test-agent/1.0";
    private static final String AUTH_GROUP = StorageRole.ADMIN;

    private final StorageAuditEvents events = new StorageAuditEvents(USER, IP, AGENT, AUTH_GROUP);

    @SuppressWarnings("unchecked")
    private Map<String, Object> getAuditLog(AuditPayload payload) {
        return (Map<String, Object>) payload.get("auditLog");
    }

    @Test
    public void testCreateOrUpdateRecordsSuccess() {
        List<String> resources = Collections.singletonList("record1");
        AuditPayload payload = events.getCreateOrUpdateRecordsEventSuccess(resources);
        Map<String, Object> log = getAuditLog(payload);

        assertEquals("ST001", log.get("actionId"));
        assertEquals(AuditAction.UPDATE, log.get("action"));
        assertEquals(AuditStatus.SUCCESS, log.get("status"));
        assertEquals(USER, log.get("user"));
        assertEquals(IP, log.get("userIpAddress"));
        assertEquals(AGENT, log.get("userAgent"));
        assertEquals(AUTH_GROUP, log.get("userAuthorizedGroupName"));
        assertEquals(Arrays.asList(StorageRole.CREATOR, StorageRole.ADMIN),
                log.get("requiredGroupsForAction"));
    }

    @Test
    public void testDeleteRecordEventFail() {
        List<String> resources = Collections.singletonList("record1");
        AuditPayload payload = events.getDeleteRecordEventFail(resources);
        Map<String, Object> log = getAuditLog(payload);

        assertEquals("ST002", log.get("actionId"));
        assertEquals(AuditAction.DELETE, log.get("action"));
        assertEquals(AuditStatus.FAILURE, log.get("status"));
        assertEquals(Arrays.asList(StorageRole.CREATOR, StorageRole.ADMIN),
                log.get("requiredGroupsForAction"));
    }

    @Test
    public void testPurgeRecordSuccess() {
        List<String> resources = Collections.singletonList("record1");
        AuditPayload payload = events.getPurgeRecordEventSuccess(resources);
        Map<String, Object> log = getAuditLog(payload);

        assertEquals("ST003", log.get("actionId"));
        assertEquals(Collections.singletonList(StorageRole.ADMIN),
                log.get("requiredGroupsForAction"));
    }

    @Test
    public void testReadMultipleRecordsSuccess() {
        List<String> resources = Arrays.asList("r1", "r2");
        AuditPayload payload = events.getReadMultipleRecordsSuccess(resources);
        Map<String, Object> log = getAuditLog(payload);

        assertEquals("ST007", log.get("actionId"));
        assertEquals(AuditAction.READ, log.get("action"));
        assertEquals(AuditStatus.SUCCESS, log.get("status"));
        assertEquals(Arrays.asList(StorageRole.VIEWER, StorageRole.CREATOR, StorageRole.ADMIN),
                log.get("requiredGroupsForAction"));
    }

    @Test
    public void testPurgeRecordVersionsSuccess() {
        List<String> resources = Arrays.asList("v1", "v2");
        AuditPayload payload = events.getPurgeRecordVersionsEventSuccess("recordId1", resources);
        Map<String, Object> log = getAuditLog(payload);

        assertEquals("ST015", log.get("actionId"));
        assertEquals("Record `recordId1` versions purged", log.get("message"));
        assertEquals(Collections.singletonList(StorageRole.ADMIN),
                log.get("requiredGroupsForAction"));
    }

    @Test
    public void testCreateReplayRequestSuccess() {
        List<String> resources = Collections.singletonList("replay1");
        AuditPayload payload = events.getCreateReplayRequestSuccess(resources);
        Map<String, Object> log = getAuditLog(payload);

        assertEquals("ST016", log.get("actionId"));
        assertEquals("Replay started", log.get("message"));
        assertEquals(AuditAction.CREATE, log.get("action"));
    }

    @Test
    public void testNullContextFieldsDefaultToUnknown() {
        StorageAuditEvents nullContextEvents = new StorageAuditEvents(USER, null, null, null);
        AuditPayload payload = nullContextEvents.getCreateOrUpdateRecordsEventSuccess(
                Collections.singletonList("r1"));
        Map<String, Object> log = getAuditLog(payload);

        assertEquals("0.0.0.0", log.get("userIpAddress"));
        assertEquals("unknown", log.get("userAgent"));
        assertEquals("unknown", log.get("userAuthorizedGroupName"));
    }

    @Test
    public void testNullUserDefaultsToUnknown() {
        StorageAuditEvents nullUserEvents = new StorageAuditEvents(null, IP, AGENT, AUTH_GROUP);
        AuditPayload payload = nullUserEvents.getCreateOrUpdateRecordsEventSuccess(
                Collections.singletonList("r1"));
        Map<String, Object> log = getAuditLog(payload);
        assertEquals("unknown", log.get("user"));
    }
}
