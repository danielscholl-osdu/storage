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

package org.opengroup.osdu.storage.records;

import com.google.api.client.util.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class CollaborationRecordsIntegrationTest extends TestBase {
    protected static final String COLLABORATION_HEADER = "x-collaboration";
    protected static final String APPLICATION_NAME = "storage service integration test";
    protected static final String TENANT_NAME = TenantUtils.getTenantName();
    protected static final long CURRENT_TIME_MILLIS = System.currentTimeMillis();
    protected static final String COLLABORATION1_ID = UUID.randomUUID().toString();
    protected static final String COLLABORATION2_ID = UUID.randomUUID().toString();
    protected static final String RECORD_ID_1 = TENANT_NAME + ":inttest:1" + CURRENT_TIME_MILLIS;
    protected static final String RECORD_ID_2 = TENANT_NAME + ":inttest:2" + CURRENT_TIME_MILLIS;
    protected static final String RECORD_ID_3 = TENANT_NAME + ":inttest:3" + CURRENT_TIME_MILLIS;

    protected static final String RECORD_PURGE_ID = TENANT_NAME + ":inttestpurge:1" + CURRENT_TIME_MILLIS;
    protected static final String KIND1 = TENANT_NAME + ":ds:inttest:1" + CURRENT_TIME_MILLIS;
    protected static final String KIND2 = TENANT_NAME + ":ds:inttest:2" + CURRENT_TIME_MILLIS;
    protected static Long RECORD1_V1;
    protected static Long RECORD1_V2;
    protected static Long RECORD1_V3;
    protected static Long RECORD1_V4;
    protected static Long RECORD2_V1;
    protected static Long RECORD2_V2;
    protected static Long RECORD3_V1;
    protected static Long RECORD3_V2;

    protected static Long RECORD_PURGE_V1;
    protected static Long RECORD_PURGE_V2;
    protected static Long RECORD_PURGE_V3;
    protected static String LEGAL_TAG_NAME_A;

    public static void classSetup(String token) throws Exception {
        LEGAL_TAG_NAME_A = LegalTagUtils.createRandomName();
        LegalTagUtils.create(LEGAL_TAG_NAME_A, token);

        RECORD1_V1 = createRecord(RECORD_ID_1, null, KIND1, token);
        RECORD1_V2 = createRecord(RECORD_ID_1, COLLABORATION1_ID, KIND1, token);
        RECORD1_V3 = createRecord(RECORD_ID_1, COLLABORATION1_ID, KIND1, token);
        RECORD1_V4 = createRecord(RECORD_ID_1, COLLABORATION2_ID, KIND1, token);

        RECORD2_V1 = createRecord(RECORD_ID_2, null, KIND1, token);
        RECORD2_V2 = createRecord(RECORD_ID_2, COLLABORATION2_ID, KIND1, token);

        RECORD3_V1 = createRecord(RECORD_ID_3, COLLABORATION1_ID, KIND2, token);
        RECORD3_V2 = createRecord(RECORD_ID_3, COLLABORATION2_ID, KIND2, token);

        //for purge integration test
        RECORD_PURGE_V1 = createRecord(RECORD_PURGE_ID, COLLABORATION1_ID, KIND1, token);
        RECORD_PURGE_V2 = createRecord(RECORD_PURGE_ID, COLLABORATION1_ID, KIND1, token);
        RECORD_PURGE_V3 = createRecord(RECORD_PURGE_ID, COLLABORATION2_ID, KIND1, token);
    }

    public static void classTearDown(String token) throws Exception {
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", getHeadersWithxCollaboration(null, token), "", "");
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, token), "", "");
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", getHeadersWithxCollaboration(COLLABORATION2_ID, token), "", "");
        TestUtils.send("records/" + RECORD_ID_2, "DELETE", getHeadersWithxCollaboration(null, token), "", "");
        TestUtils.send("records/" + RECORD_ID_2, "DELETE", getHeadersWithxCollaboration(COLLABORATION2_ID, token), "", "");
        TestUtils.send("records/" + RECORD_ID_3, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, token), "", "");
        TestUtils.send("records/" + RECORD_ID_3, "DELETE", getHeadersWithxCollaboration(COLLABORATION2_ID, token), "", "");
        LegalTagUtils.delete(LEGAL_TAG_NAME_A, token);
    }

    @Test
    public void should_getLatestVersion_when_validRecordIdAndCollaborationIdAreProvided() throws Exception {
        //get record1 --> v1
        ClientResponse response = TestUtils.send("records/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(null, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD1_V1);
        //get record1 with guid1 --> v3
        response = TestUtils.send("records/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD1_V3);
        //get record1 with guid2 --> v4
        response = TestUtils.send("records/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(COLLABORATION2_ID, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD1_V4);
        //get record2 with guid1 --> 404
        response = TestUtils.send("records/" + RECORD_ID_2, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    public void should_getCorrectRecordVersion_when_validRecordIdAndCollaborationIdAndRecordVersionAreProvided() throws Exception {
        //get record1 with v2 with context guid1
        ClientResponse response = TestUtils.send("records/" + RECORD_ID_1 + "/" + RECORD1_V2, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD1_V2);
        //get 404 for record1 with v2 with context guid2
        response = TestUtils.send("records/" + RECORD_ID_1 + "/" + RECORD1_V2, "GET", getHeadersWithxCollaboration(COLLABORATION2_ID, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    public void should_getAllRecordVersions_when_validRecordIdAndCollaborationIdAreProvided() throws Exception {
        //I will get only v1 for record1 with no context
        ClientResponse response = TestUtils.send("records/versions/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(null, testUtils.getToken()), "", "");
        RecordsApiAcceptanceTests.GetVersionsResponse versionsResponse = TestUtils.getResult(response, 200, RecordsApiAcceptanceTests.GetVersionsResponse.class);
        assertEquals(1, versionsResponse.versions.length);
        assertEquals(RECORD1_V1, versionsResponse.versions[0]);


        //I will get v2 and v3 for record1 with context guid1
        response = TestUtils.send("records/versions/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, testUtils.getToken()), "", "");
        versionsResponse = TestUtils.getResult(response, 200, RecordsApiAcceptanceTests.GetVersionsResponse.class);
        assertEquals(2, versionsResponse.versions.length);
        List<Long> versions = Arrays.asList(versionsResponse.versions);
        assertTrue(versions.contains(RECORD1_V2));
        assertTrue(versions.contains(RECORD1_V3));
    }

    @Test
    public void should_purgeAllRecordVersionsOnlyInCollaborationContext() throws Exception {
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

    private static void assertRecordVersion(ClientResponse response, Long expectedVersion) {
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        String responseBody = response.getEntity(String.class);
        DummyRecordsHelper.RecordResultMock result = GSON.fromJson(responseBody, DummyRecordsHelper.RecordResultMock.class);
        assertEquals(expectedVersion.longValue(), Long.parseLong(result.version));
    }

    private static Map<String, String> getHeadersWithxCollaboration(String collaborationId, String token) {
        Map<String, String> headers = HeaderUtils.getHeaders(TENANT_NAME, token);
        if (!Strings.isNullOrEmpty(collaborationId)) {
            headers.put(COLLABORATION_HEADER, "id=" + collaborationId + ",application=" + APPLICATION_NAME);
        }
        return headers;
    }

}
