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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.ConfigUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.opengroup.osdu.storage.util.HeaderUtils.getHeaders;
import static org.opengroup.osdu.storage.util.TestUtils.createRecordWithoutCollaborationContext_AndReturnVersion;

@Slf4j
@DisplayName("Patch Record API Tests")
public class PatchRecordTest extends TestBase {

    // Constants for better performance and maintainability
    private static final String TENANT_NAME = TenantUtils.getTenantName();
    private static final String TIMESTAMP = String.valueOf(System.currentTimeMillis());
    private static final String RECORD_ID = TENANT_NAME + ":patchRecord:test" + TIMESTAMP;
    private static final String KIND = TENANT_NAME + ":ds:patchRecord:" + TIMESTAMP;
    private static final String MERGE_PATCH_CONTENT_TYPE = "application/merge-patch+json";

    // Patch body constants for reusability and performance
    private static final String DATA_PATCH_BODY = """
        {
          "data": {
            "wellName": "Updated Well Name",
            "status": "active",
            "newField": "newValue"
          }
        }""";

    private static final String TAGS_PATCH_BODY = """
        {
          "tags": {
            "environment": "production",
            "project": "test-project"
          }
        }""";

    private static final String SOFT_DELETE_PATCH = "{\"deleted\":true}";
    private static final String RECOVERY_PATCH = "{\"deleted\":false}";
    private static final String INVALID_JSON_PATCH = "{\"data\":{\"field\":}"; // Missing value

    private String legalTagName;

    @BeforeEach
    @Override
    public void setup() throws Exception {
        this.testUtils = new TokenTestUtils();
        this.configUtils = new ConfigUtils("test.properties");

        legalTagName = LegalTagUtils.createRandomName();
        LegalTagUtils.create(legalTagName, testUtils.getToken());
        createRecordWithoutCollaborationContext_AndReturnVersion(
                RECORD_ID, KIND, legalTagName, TENANT_NAME, testUtils.getToken());
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        try {
            TestUtils.send("records/" + RECORD_ID, "DELETE",
                    getHeaders(TENANT_NAME, testUtils.getToken()), "", "");
        } catch (Exception e) {
            log.warn("Failed to delete test record: {}", RECORD_ID, e);
        }

        try {
            LegalTagUtils.delete(legalTagName, testUtils.getToken());
        } catch (Exception e) {
            log.warn("Failed to delete legal tag: {}", legalTagName, e);
        }

        this.testUtils = null;
        this.configUtils = null;
    }

    @Test
    public void should_patchRecordData_successfully() throws Exception {
        // Patch the record
        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records/" + RECORD_ID, "PATCH",
                getHeaders(TENANT_NAME, testUtils.getToken()), MERGE_PATCH_CONTENT_TYPE, DATA_PATCH_BODY, "");

        assertEquals(HttpStatus.SC_OK, patchResponse.getCode());

        String responseBody = EntityUtils.toString(patchResponse.getEntity());
        JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();

        validatePatchResponse(responseJson);
        validateDataFields(responseJson);
    }

    @Test
    public void should_patchRecordTags_successfully() throws Exception {
        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records/" + RECORD_ID, "PATCH",
                getHeaders(TENANT_NAME, testUtils.getToken()), MERGE_PATCH_CONTENT_TYPE, TAGS_PATCH_BODY, "");

        assertEquals(HttpStatus.SC_OK, patchResponse.getCode());

        String responseBody = EntityUtils.toString(patchResponse.getEntity());
        JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();

        assertTrue(responseJson.has("tags"), "Response should contain 'tags' field");
        JsonObject tags = responseJson.getAsJsonObject("tags");
        assertEquals("production", tags.get("environment").getAsString());
        assertEquals("test-project", tags.get("project").getAsString());
    }

    @Test
    public void should_softDeleteRecord_successfully() throws Exception {
        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records/" + RECORD_ID, "PATCH",
                getHeaders(TENANT_NAME, testUtils.getToken()), MERGE_PATCH_CONTENT_TYPE, SOFT_DELETE_PATCH, "");

        assertEquals(HttpStatus.SC_OK, patchResponse.getCode());

        // Verify soft deletion - record should not be accessible via normal GET
        CloseableHttpResponse getRecordResponse = TestUtils.send("records/" + RECORD_ID, "GET",
                getHeaders(TENANT_NAME, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, getRecordResponse.getCode());
    }

    @Test
    public void should_softDeleteAndRecover_successfully() throws Exception {
        // Soft delete
        CloseableHttpResponse deleteResponse = TestUtils.sendWithCustomMediaType("records/" + RECORD_ID, "PATCH",
                getHeaders(TENANT_NAME, testUtils.getToken()), MERGE_PATCH_CONTENT_TYPE, SOFT_DELETE_PATCH, "");
        assertEquals(HttpStatus.SC_OK, deleteResponse.getCode());

        // Verify deletion
        CloseableHttpResponse getDeletedResponse = TestUtils.send("records/" + RECORD_ID, "GET",
                getHeaders(TENANT_NAME, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, getDeletedResponse.getCode());

        // Recover record
        CloseableHttpResponse recoverResponse = TestUtils.sendWithCustomMediaType("records/" + RECORD_ID, "PATCH",
                getHeaders(TENANT_NAME, testUtils.getToken()), MERGE_PATCH_CONTENT_TYPE, RECOVERY_PATCH, "");
        assertEquals(HttpStatus.SC_OK, recoverResponse.getCode());

        // Verify recovery
        CloseableHttpResponse getRecoveredResponse = TestUtils.send("records/" + RECORD_ID, "GET",
                getHeaders(TENANT_NAME, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_OK, getRecoveredResponse.getCode());
    }

    @Test
    public void should_FailOnPatchingSoftDeletedRecord() throws Exception {
        // Soft delete
        CloseableHttpResponse deleteResponse = TestUtils.sendWithCustomMediaType("records/" + RECORD_ID, "PATCH",
                getHeaders(TENANT_NAME, testUtils.getToken()), MERGE_PATCH_CONTENT_TYPE, SOFT_DELETE_PATCH, "");
        assertEquals(HttpStatus.SC_OK, deleteResponse.getCode());

        // try to patch Tags, should return bad request
        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records/" + RECORD_ID, "PATCH",
                getHeaders(TENANT_NAME, testUtils.getToken()), MERGE_PATCH_CONTENT_TYPE, TAGS_PATCH_BODY, "");
        assertEquals(HttpStatus.SC_BAD_REQUEST, patchResponse.getCode());
    }

    @Test
    public void should_returnBadRequest_whenRecordIdInvalid() throws Exception {
        String invalidRecordId = "invalid-record-id-format";
        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records/" + invalidRecordId, "PATCH",
                getHeaders(TENANT_NAME, testUtils.getToken()), MERGE_PATCH_CONTENT_TYPE, DATA_PATCH_BODY, "");
        assertEquals(HttpStatus.SC_BAD_REQUEST, patchResponse.getCode());
    }

    @Test
    public void should_returnNotFound_whenRecordDoesNotExist() throws Exception {
        String nonExistentRecordId = TENANT_NAME + ":nonExistent:record" + TIMESTAMP;
        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records/" + nonExistentRecordId, "PATCH",
                getHeaders(TENANT_NAME, testUtils.getToken()), MERGE_PATCH_CONTENT_TYPE, DATA_PATCH_BODY, "");
        assertEquals(HttpStatus.SC_NOT_FOUND, patchResponse.getCode());
    }

    @Test
    public void should_returnUnauthorized_whenTokenNotProvided() throws Exception {
        Map<String, String> headersWithoutToken = getHeaders(TENANT_NAME, null);
        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records/" + RECORD_ID, "PATCH",
                headersWithoutToken, MERGE_PATCH_CONTENT_TYPE, DATA_PATCH_BODY, "");
        assertEquals(HttpStatus.SC_UNAUTHORIZED, patchResponse.getCode());
    }

    @Test
    public void should_returnBadRequest_whenPatchBodyInvalid() throws Exception {
        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records/" + RECORD_ID, "PATCH",
                getHeaders(TENANT_NAME, testUtils.getToken()), MERGE_PATCH_CONTENT_TYPE, INVALID_JSON_PATCH, "");
        assertEquals(HttpStatus.SC_BAD_REQUEST, patchResponse.getCode());
    }

    // Helper methods for comprehensive validation
    private void validatePatchResponse(JsonObject responseJson) {
        assertNotNull(responseJson, "Response should not be null");
        assertTrue(responseJson.has("id"), "Response should contain 'id' field");
        assertTrue(responseJson.has("version"), "Response should contain 'version' field");
        assertTrue(responseJson.has("data"), "Response should contain 'data' field");

        assertEquals(RECORD_ID, responseJson.get("id").getAsString(), "Record ID should match");}

    private void validateDataFields(JsonObject responseJson) {
        JsonObject data = responseJson.getAsJsonObject("data");
        assertNotNull(data, "Data field should not be null");

        assertEquals("Updated Well Name", data.get("wellName").getAsString());
        assertEquals("active", data.get("status").getAsString());
        assertEquals("newValue", data.get("newField").getAsString());
    }
}
