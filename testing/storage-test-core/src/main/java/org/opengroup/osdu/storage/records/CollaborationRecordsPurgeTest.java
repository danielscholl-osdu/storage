package org.opengroup.osdu.storage.records;

import com.google.api.client.util.Strings;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Test;
import org.opengroup.osdu.storage.util.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.apache.http.HttpStatus.*;
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
        CloseableHttpResponse response = TestUtils.send("records/" + RECORD_PURGE_ID, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, testUtils.getToken()), "", "");
        assertEquals(SC_NO_CONTENT, response.getCode());
        response = TestUtils.send("records/" + RECORD_PURGE_ID, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, testUtils.getToken()), "", "");
        assertEquals(SC_NOT_FOUND, response.getCode());
        response = TestUtils.send("records/" + RECORD_PURGE_ID, "GET", getHeadersWithxCollaboration(COLLABORATION2_ID, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD_PURGE_V3);
    }

    private static Long createRecord(String recordId, String collaborationId, String kind, String token) throws Exception {
        String jsonInput = RecordUtil.createDefaultJsonRecord(recordId, kind, LEGAL_TAG_NAME_A);

        CloseableHttpResponse response = TestUtils.send("records", "PUT", getHeadersWithxCollaboration(collaborationId, token), jsonInput, "");
        assertEquals(SC_CREATED, response.getCode());
        assertTrue(response.getEntity().getContentType().contains("application/json"));

        String responseBody = EntityUtils.toString(response.getEntity());
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

    private static void assertRecordVersion(CloseableHttpResponse response, Long expectedVersion) {
        assertEquals(HttpStatus.SC_OK, response.getCode());

        String responseBody = null;
        try {
            responseBody = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        DummyRecordsHelper.RecordResultMock result = GSON.fromJson(responseBody, DummyRecordsHelper.RecordResultMock.class);
        assertEquals(expectedVersion.longValue(), Long.parseLong(result.version));
    }
}
