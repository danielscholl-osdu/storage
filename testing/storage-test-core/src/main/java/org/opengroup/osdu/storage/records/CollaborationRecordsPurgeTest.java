package org.opengroup.osdu.storage.records;

import com.google.api.client.util.Strings;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Test;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import java.util.Map;
import java.util.UUID;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class CollaborationRecordsPurgeTest extends TestBase {
    private static boolean isCollaborationEnabled = false;
    protected static final String COLLABORATION_HEADER = "x-collaboration";
    protected static final String APPLICATION_NAME = "storage service integration test";
    protected static final String TENANT_NAME = TenantUtils.getTenantName();
    private static final long CURRENT_TIME_MILLIS = System.currentTimeMillis();
    private static final String RECORD_PURGE_ID = TENANT_NAME + ":inttestpurge:1" + CURRENT_TIME_MILLIS;
    protected static final String COLLABORATION1_ID = UUID.randomUUID().toString();
    protected static final String COLLABORATION2_ID = UUID.randomUUID().toString();
    private static final String KIND1 = TENANT_NAME + ":ds:inttest:1" + CURRENT_TIME_MILLIS;
    private static Long RECORD_PURGE_V1;
    private static Long RECORD_PURGE_V2;
    private static Long RECORD_PURGE_V3;
    private static String LEGAL_TAG_NAME_A;

    @Override
    public void setup() throws Exception {
        if (configUtils != null && !configUtils.getIsCollaborationEnabled()) {
            return;
        }
        isCollaborationEnabled = true;
        LEGAL_TAG_NAME_A = LegalTagUtils.createRandomName();
        LegalTagUtils.create(LEGAL_TAG_NAME_A, testUtils.getToken());

        RECORD_PURGE_V1 = createRecord(RECORD_PURGE_ID, COLLABORATION1_ID, KIND1, testUtils.getToken());
        RECORD_PURGE_V2 = createRecord(RECORD_PURGE_ID, COLLABORATION1_ID, KIND1, testUtils.getToken());
        RECORD_PURGE_V3 = createRecord(RECORD_PURGE_ID, COLLABORATION2_ID, KIND1, testUtils.getToken());
    }

    @After
    public void tearDown() throws Exception {
        if (!isCollaborationEnabled) return;
        TestUtils.send("records/" + RECORD_PURGE_ID, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_PURGE_ID, "DELETE", getHeadersWithxCollaboration(COLLABORATION2_ID, testUtils.getToken()), "", "");
        LegalTagUtils.delete(LEGAL_TAG_NAME_A, testUtils.getToken());
    }

    @Test
    public void should_purgeAllRecordVersionsOnlyInCollaborationContext() throws Exception {
        if (!isCollaborationEnabled) return;
        ClientResponse response = TestUtils.send("records/" + RECORD_PURGE_ID, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, testUtils.getToken()), "", "");
        assertEquals(SC_NO_CONTENT, response.getStatus());
        response = TestUtils.send("records/" + RECORD_PURGE_ID, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, testUtils.getToken()), "", "");
        assertEquals(SC_NOT_FOUND, response.getStatus());
        response = TestUtils.send("records/" + RECORD_PURGE_ID, "GET", getHeadersWithxCollaboration(COLLABORATION2_ID, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD_PURGE_V3);
    }

    private static Long createRecord(String recordId, String collaborationId, String kind, String token) throws Exception {
        String jsonInput = RecordUtil.createDefaultJsonRecord(recordId, kind, LEGAL_TAG_NAME_A);

        ClientResponse response = TestUtils.send("records", "PUT", getHeadersWithxCollaboration(collaborationId, token), jsonInput, "");
        assertEquals(SC_CREATED, response.getStatus());
        assertTrue(response.getType().toString().contains("application/json"));

        String responseBody = response.getEntity(String.class);
        DummyRecordsHelper.CreateRecordResponse result = GSON.fromJson(responseBody, DummyRecordsHelper.CreateRecordResponse.class);

        return Long.parseLong(result.recordIdVersions[0].split(":")[3]);
    }

    private static Map<String, String> getHeadersWithxCollaboration(String collaborationId, String token) {
        Map<String, String> headers = HeaderUtils.getHeaders(TENANT_NAME, token);
        if (!Strings.isNullOrEmpty(collaborationId)) {
            headers.put(COLLABORATION_HEADER, "id=" + collaborationId + ",application=" + APPLICATION_NAME);
        }
        return headers;
    }

    private static void assertRecordVersion(ClientResponse response, Long expectedVersion) {
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        String responseBody = response.getEntity(String.class);
        DummyRecordsHelper.RecordResultMock result = GSON.fromJson(responseBody, DummyRecordsHelper.RecordResultMock.class);
        assertEquals(expectedVersion.longValue(), Long.parseLong(result.version));
    }
}
