/*
 * Copyright 2025 bp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.records;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.ConfigUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengroup.osdu.storage.util.HeaderUtils.getHeaders;
import static org.opengroup.osdu.storage.util.TestUtils.createRecordWithoutCollaborationContext_AndReturnVersion;

@Slf4j
public class GetAllRecordsTest extends TestBase {
    private static String LEGAL_TAG_NAME;
    private static final String TENANT_NAME = TenantUtils.getTenantName();
    private static final String TIMESTAMP = String.valueOf(System.currentTimeMillis());
    private static final String RECORD_ID_1 = TENANT_NAME + ":readAllRecords:1" + TIMESTAMP;
    private static final String RECORD_ID_2 = TENANT_NAME + ":readAllRecords:2" + TIMESTAMP;
    private static final String RECORD_ID_3 = TENANT_NAME + ":readAllRecords:3" + TIMESTAMP;
    private static final String KIND = TENANT_NAME + ":ds:readAllRecords:" + TIMESTAMP;
    private List<String> generatedRecordsList = null;
    private List<String> softDeletedRecordsList = null;

    @BeforeEach
    @Override
    public void setup() throws Exception {
        this.testUtils = new TokenTestUtils();
        this.configUtils = new ConfigUtils("test.properties");
        generatedRecordsList = new ArrayList<>();
        softDeletedRecordsList = new ArrayList<>();

        LEGAL_TAG_NAME = LegalTagUtils.createRandomName();
        LegalTagUtils.create(LEGAL_TAG_NAME, testUtils.getToken());
        createRecordWithoutCollaborationContext_AndReturnVersion(RECORD_ID_1, KIND, LEGAL_TAG_NAME, TENANT_NAME, testUtils.getToken());
        createRecordWithoutCollaborationContext_AndReturnVersion(RECORD_ID_2, KIND, LEGAL_TAG_NAME, TENANT_NAME, testUtils.getToken());
        createRecordWithoutCollaborationContext_AndReturnVersion(RECORD_ID_3, KIND, LEGAL_TAG_NAME, TENANT_NAME, testUtils.getToken());

        generatedRecordsList.add(RECORD_ID_1);
        generatedRecordsList.add(RECORD_ID_3);
        generatedRecordsList.add(RECORD_ID_2);
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", getHeaders(TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_2, "DELETE", getHeaders(TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_3, "DELETE", getHeaders(TENANT_NAME, testUtils.getToken()), "", "");

        LegalTagUtils.delete(LEGAL_TAG_NAME, testUtils.getToken());

        this.testUtils = null;
        this.configUtils = null;
        generatedRecordsList = null;
        softDeletedRecordsList = null;
    }

    private void softDeleteRecord(String recordId, String tenantName, String token) throws Exception {
        CloseableHttpResponse response = TestUtils.send("records/" + recordId + ":delete", "POST", getHeaders(tenantName, token), "", "");
        assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());
    }

    @Test
    public void should_fetchAllRecords() throws Exception {
        //get all records
        CloseableHttpResponse getAllRecordsResponse = TestUtils.send("records", "GET",
                getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?kind=" + KIND);
        assertEquals(HttpStatus.SC_OK, getAllRecordsResponse.getCode());

        String responseBody = EntityUtils.toString(getAllRecordsResponse.getEntity());
        JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
        verifyGetAllActiveRecordsSuccessResponse(responseJson);
    }

    @Test
    public void should_fetchOnlyActiveRecordsWhenNoDeleteFilterSpecified() throws Exception {
        softDeleteRecord(RECORD_ID_2, TENANT_NAME, testUtils.getToken());
        softDeletedRecordsList.add(RECORD_ID_2);

        //get all records
        CloseableHttpResponse getAllRecordsResponse = TestUtils.send("records", "GET",
                getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?kind=" + KIND);
        assertEquals(HttpStatus.SC_OK, getAllRecordsResponse.getCode());

        String responseBody = EntityUtils.toString(getAllRecordsResponse.getEntity());
        JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
        verifyGetAllActiveRecordsSuccessResponse(responseJson);
    }

    @Test
    public void should_fetchSoftDeletedRecordsWhenFilterSpecified() throws Exception {
        softDeleteRecord(RECORD_ID_2, TENANT_NAME, testUtils.getToken());
        softDeletedRecordsList.add(RECORD_ID_2);

        //get all records
        CloseableHttpResponse getAllRecordsResponse = TestUtils.send("records", "GET",
                getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?deleted=true&kind=" + KIND);
        assertEquals(HttpStatus.SC_OK, getAllRecordsResponse.getCode());

        String responseBody = EntityUtils.toString(getAllRecordsResponse.getEntity());
        JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();

        // Assert that the response has a "results" array with required objects
        assertTrue(responseJson.has("results"), "Response should contain 'results' field");
        JsonArray resultsArray = responseJson.getAsJsonArray("results");
        assertEquals(softDeletedRecordsList.size(), resultsArray.size(), "Results array should contain exactly one object");

        JsonObject inactiveRecord = resultsArray.get(0).getAsJsonObject();
        assertEquals(RECORD_ID_2, inactiveRecord.get("id").getAsString());
    }

    @Test
    public void should_returnUnauthorized_whenTokenNotProvided() throws Exception {
        CloseableHttpResponse getRecordsResponse = TestUtils.send("records/", "GET",
                getHeaders(TENANT_NAME, null), "", "");
        assertEquals(HttpStatus.SC_UNAUTHORIZED, getRecordsResponse.getCode());
    }

    @Test
    public void should_returnBadRequest_whenDataPartitionHeaderMissing() throws Exception {
        Map<String, String> headers = getHeaders(TENANT_NAME, testUtils.getToken());
        headers.remove("Data-Partition-Id");

        CloseableHttpResponse getRecordsResponse = TestUtils.send("records/", "GET",
                headers, "", "?kind=" + KIND);

        assertEquals(HttpStatus.SC_BAD_REQUEST, getRecordsResponse.getCode());
    }

    @Test
    public void should_returnBadRequest_when_limitExceedsMaximum() throws Exception {
        CloseableHttpResponse response = TestUtils.send("records", "GET",
                getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=101&kind=" + KIND);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
    }

    @Test
    public void should_returnBadRequest_when_limitIsZero() throws Exception {
        CloseableHttpResponse response = TestUtils.send("records", "GET",
                getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=0&kind=" + KIND);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
    }

    @Test
    public void should_returnBadRequest_when_invalidKindParamPassed() throws Exception {
        CloseableHttpResponse response = TestUtils.send("records", "GET",
                getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?kind=invalid-kind-format");
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
    }

    private void verifyGetAllActiveRecordsSuccessResponse(JsonObject responseJson) {
        // Assert that the response has a "results" array with required objects
        assertTrue(responseJson.has("results"), "Response should contain 'results' field");
        JsonArray resultsArray = responseJson.getAsJsonArray("results");
        assertEquals(generatedRecordsList.size() - softDeletedRecordsList.size(), resultsArray.size(), "Results array should contain all active records.");

        JsonObject record = resultsArray.get(0).getAsJsonObject();
        String recordId = record.get("id").getAsString();
        if (!(RECORD_ID_1.equalsIgnoreCase(recordId) || RECORD_ID_2.equalsIgnoreCase(recordId) || RECORD_ID_3.equalsIgnoreCase(recordId))) {
            Assertions.fail("Results array should contain one of the generated Records");
        }
        assertEquals(KIND, record.get("kind").getAsString());
    }
}
