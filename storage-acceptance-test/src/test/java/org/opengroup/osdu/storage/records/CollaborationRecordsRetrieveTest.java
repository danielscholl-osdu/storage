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

import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.opengroup.osdu.storage.util.HeaderUtils.getHeadersWithxCollaboration;
import static org.opengroup.osdu.storage.util.TestUtils.assertRecordVersion;
import static org.opengroup.osdu.storage.util.TestUtils.createRecordInCollaborationContext_AndReturnVersion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.ConfigUtils;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class CollaborationRecordsRetrieveTest extends TestBase {

    private static boolean isCollaborationEnabled = false;
    private static final DummyRecordsHelper RECORDS_HELPER = new DummyRecordsHelper();
    private static final String APPLICATION_NAME = "storage service integration test";
    private static final String TENANT_NAME = TenantUtils.getTenantName();
    private static final long CURRENT_TIME_MILLIS = System.currentTimeMillis();
    private static final String COLLABORATION1_ID = UUID.randomUUID().toString();
    private static final String COLLABORATION2_ID = UUID.randomUUID().toString();
    private static final String RECORD_ID_1 = TENANT_NAME + ":inttest:1" + CURRENT_TIME_MILLIS;
    private static final String RECORD_ID_2 = TENANT_NAME + ":inttest:2" + CURRENT_TIME_MILLIS;
    private static final String RECORD_ID_3 = TENANT_NAME + ":inttest:3" + CURRENT_TIME_MILLIS;
    private static final String KIND1 = TENANT_NAME + ":ds:inttest:1" + CURRENT_TIME_MILLIS;
    private static final String KIND2 = TENANT_NAME + ":ds:inttest:2" + CURRENT_TIME_MILLIS;
    private static final String KIND3 = TENANT_NAME + ":ds:inttest:3" + CURRENT_TIME_MILLIS;
    private static Long RECORD1_V1;
    private static Long RECORD1_V2;
    private static Long RECORD1_V3;
    private static Long RECORD1_V4;
    private static Long RECORD2_V1;
    private static Long RECORD2_V2;
    private static Long RECORD3_V1;
    private static Long RECORD3_V2;
    private static String LEGAL_TAG_NAME_A;

    @Override
    public void setup() throws Exception {
        this.testUtils = new TokenTestUtils();
        this.configUtils = new ConfigUtils("test.properties");

        if (configUtils != null && !configUtils.getIsCollaborationEnabled()) {
            return;
        }
        isCollaborationEnabled = true;
        LEGAL_TAG_NAME_A = LegalTagUtils.createRandomName();
        LegalTagUtils.create(LEGAL_TAG_NAME_A, testUtils.getToken());

        RECORD1_V1 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_1, KIND1, LEGAL_TAG_NAME_A, null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD1_V2 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_1, KIND1, LEGAL_TAG_NAME_A, COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD1_V3 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_1, KIND1, LEGAL_TAG_NAME_A, COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD1_V4 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_1, KIND1, LEGAL_TAG_NAME_A, COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());

        RECORD2_V1 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_2, KIND1, LEGAL_TAG_NAME_A, null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD2_V2 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_2, KIND1, LEGAL_TAG_NAME_A, COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());

        RECORD3_V1 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_3, KIND2, LEGAL_TAG_NAME_A, COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD3_V2 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_3, KIND2, LEGAL_TAG_NAME_A, COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (!isCollaborationEnabled) return;
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_2, "DELETE", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_2, "DELETE", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_3, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_3, "DELETE", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        LegalTagUtils.delete(LEGAL_TAG_NAME_A, testUtils.getToken());

        this.testUtils = null;
        this.configUtils = null;
    }

    @Test
    public void should_getLatestVersion_when_validRecordIdAndCollaborationIdAreProvided() throws Exception {
        if (!isCollaborationEnabled) return;
        //get record1 --> v1
        CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD1_V1);
        //get record1 with guid1 --> v3
        response = TestUtils.send("records/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD1_V3);
        //get record1 with guid2 --> v4
        response = TestUtils.send("records/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD1_V4);
        //get record2 with guid1 --> 404
        response = TestUtils.send("records/" + RECORD_ID_2, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getCode());
    }

    @Test
    public void should_getCorrectRecordVersion_when_validRecordIdAndCollaborationIdAndRecordVersionAreProvided() throws Exception {
        if (!isCollaborationEnabled) return;
        //get record1 with v2 with context guid1
        CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID_1 + "/" + RECORD1_V2, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD1_V2);
        //get 404 for record1 with v2 with context guid2
        response = TestUtils.send("records/" + RECORD_ID_1 + "/" + RECORD1_V2, "GET", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getCode());
    }

    @Test
    public void should_getAllRecordVersions_when_validRecordIdAndCollaborationIdAreProvided() throws Exception {
        if (!isCollaborationEnabled) return;
        //I will get only v1 for record1 with no context
        CloseableHttpResponse response = TestUtils.send("records/versions/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        RecordsApiAcceptanceTests.GetVersionsResponse versionsResponse = TestUtils.getResult(response, HttpStatus.SC_OK, RecordsApiAcceptanceTests.GetVersionsResponse.class);
        assertEquals(1, versionsResponse.versions.length);
        assertEquals(RECORD1_V1, versionsResponse.versions[0]);

        //I will get v2 and v3 for record1 with context guid1
        response = TestUtils.send("records/versions/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        versionsResponse = TestUtils.getResult(response, HttpStatus.SC_OK, RecordsApiAcceptanceTests.GetVersionsResponse.class);
        assertEquals(2, versionsResponse.versions.length);
        List<Long> versions = Arrays.asList(versionsResponse.versions);
        assertTrue(versions.contains(RECORD1_V2));
        assertTrue(versions.contains(RECORD1_V3));
    }

    @Test
    public void should_getRecordsOnlyInCollaborationContext_whenQueryByKind() throws Exception {
        if (!isCollaborationEnabled) return;
        CloseableHttpResponse response = TestUtils.send("query/records", "GET", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "?kind=" + KIND1);
        assertEquals(SC_OK, response.getCode());
        DummyRecordsHelper.QueryResultMock responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
        assertEquals(2, responseObject.results.length);
        assertTrue(Arrays.stream(responseObject.results).anyMatch(RECORD_ID_1::equals));
        assertTrue(Arrays.stream(responseObject.results).anyMatch(RECORD_ID_2::equals));

        response = TestUtils.send("query/records", "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "?kind=" + KIND1);
        assertEquals(SC_OK, response.getCode());
        responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
        assertEquals(1, responseObject.results.length);
        assertTrue(Arrays.stream(responseObject.results).anyMatch(RECORD_ID_1::equals));

        response = TestUtils.send("query/records", "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "?kind=" + KIND3);
        assertEquals(SC_OK, response.getCode());
        responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
        assertEquals(0, responseObject.results.length);
    }

    @Test
    public void should_getEmptyRecordsInNoCollaborationContext_whenQueryByKind() throws Exception {
        if (!isCollaborationEnabled) return;
        CloseableHttpResponse response = TestUtils.send("query/records", "GET", getHeadersWithxCollaboration(null, null, TENANT_NAME, testUtils.getToken()), "", "?kind=" + KIND2);
        assertEquals(SC_OK, response.getCode());
        DummyRecordsHelper.QueryResultMock responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
        assertEquals(0, responseObject.results.length);
    }

    @Test
    public void should_fetchCorrectRecords_when_validRecordIdsAndCollaborationIdAreProvided() throws Exception {
        if (!isCollaborationEnabled) return;
        //If I fetch records 1, 2,and 3 in context guid1,I should get a 200 with records 1 and 3
        JsonArray records = new JsonArray();
        records.add(RECORD_ID_1);
        records.add(RECORD_ID_2);
        records.add(RECORD_ID_3);
        JsonObject body = new JsonObject();
        body.add("records", records);
        CloseableHttpResponse response = TestUtils.send("query/records:batch", "POST", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), body.toString(), "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(2, responseObject.records.length);
        assertEquals(1, responseObject.notFound.length);
        assertEquals(0, responseObject.conversionStatuses.size());
        for (DummyRecordsHelper.RecordResultMock record : responseObject.records) {
            if (record.id.equals(RECORD_ID_1)) assertEquals(RECORD1_V3, Long.valueOf(record.version));
            else if (record.id.equals(RECORD_ID_2)) fail("should not contain record 2: " + RECORD_ID_2);
            else if (record.id.equals(RECORD_ID_3)) assertEquals(RECORD3_V1, Long.valueOf(record.version));
            else fail(String.format("should only contain record 1 %s, and record 3 %s", RECORD_ID_1, RECORD_ID_3));
        }

        // If I fetch records 1, 2, and 3 in no context, I should get a 200 with records 1 and 2
        response = TestUtils.send("query/records:batch", "POST", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), body.toString(), "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(2, responseObject.records.length);
        assertEquals(1, responseObject.notFound.length);
        assertEquals(0, responseObject.conversionStatuses.size());
        for (DummyRecordsHelper.RecordResultMock record : responseObject.records) {
            if (record.id.equals(RECORD_ID_1)) assertEquals(RECORD1_V1, Long.valueOf(record.version));
            else if (record.id.equals(RECORD_ID_2)) assertEquals(RECORD2_V1, Long.valueOf(record.version));
            else if (record.id.equals(RECORD_ID_3)) fail("should not contain record 3: " + RECORD_ID_3);
            else fail(String.format("should only contain record 1 %s, and record 2 %s", RECORD_ID_1, RECORD_ID_2));
        }
    }

    @Test
    public void should_queryAllRecords_when_validRecordIdsAndCollaborationIdAreProvided() throws Exception {
        if (!isCollaborationEnabled) return;
        // If I query records 1,2 and 3 in context guid2, I should get 200 with records 1,2 and 3
        JsonArray records = new JsonArray();
        records.add(RECORD_ID_1);
        records.add(RECORD_ID_2);
        records.add(RECORD_ID_3);
        JsonObject body = new JsonObject();
        body.add("records", records);
        CloseableHttpResponse response = TestUtils.send("query/records", "POST", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), body.toString(), "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        DummyRecordsHelper.RecordsMock responseObject = RECORDS_HELPER.getRecordsMockFromResponse(response);
        assertEquals(3, responseObject.records.length);
        assertEquals(0, responseObject.invalidRecords.length);
        assertEquals(0, responseObject.retryRecords.length);
        for (DummyRecordsHelper.RecordResultMock record : responseObject.records) {
            if (record.id.equals(RECORD_ID_1)) assertEquals(RECORD1_V4, Long.valueOf(record.version));
            else if (record.id.equals(RECORD_ID_2)) assertEquals(RECORD2_V2, Long.valueOf(record.version));
            else if (record.id.equals(RECORD_ID_3)) assertEquals(RECORD3_V2, Long.valueOf(record.version));
            else fail(String.format("should only contain record 1 %s, 2 %s and record 3 %s", RECORD_ID_1, RECORD_ID_2, RECORD_ID_3));
        }

        // If I query records 1, 2 and 3 in context guid1, I should get 2xx with records 1 and 3
        response = TestUtils.send("query/records", "POST", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), body.toString(), "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        responseObject = RECORDS_HELPER.getRecordsMockFromResponse(response);
        assertEquals(2, responseObject.records.length);
        assertEquals(1, responseObject.invalidRecords.length);
        assertEquals(0, responseObject.retryRecords.length);
        for (DummyRecordsHelper.RecordResultMock record : responseObject.records) {
            if (record.id.equals(RECORD_ID_1)) assertEquals(RECORD1_V3, Long.valueOf(record.version));
            else if (record.id.equals(RECORD_ID_2)) fail("should not contain record 2: " + RECORD_ID_2);
            else if (record.id.equals(RECORD_ID_3)) assertEquals(RECORD3_V1, Long.valueOf(record.version));
            else fail(String.format("should only contain record 1 %s, and record 3 %s", RECORD_ID_1, RECORD_ID_3));
        }
    }
}
