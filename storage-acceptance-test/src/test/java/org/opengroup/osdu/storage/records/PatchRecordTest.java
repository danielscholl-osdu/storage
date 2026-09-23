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

import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.ClientException;

import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Patch StorageRecord API Tests")
public class PatchRecordTest extends BaseRecordsAcceptanceTest {

  private static final String MERGE_PATCH_CONTENT_TYPE = "application/merge-patch+json";

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
  private static final String INVALID_JSON_PATCH = "{\"data\":{\"field\":}";

  private String recordId;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    String timestamp = String.valueOf(System.currentTimeMillis());
    recordId = getTenantId() + ":patchRecord:test" + timestamp;
    String kind = getTenantId() + ":ds:patchRecord:" + timestamp;
    String legalTagName = createLegalTagName("");
    createLegalTag(legalTagName);
    createRecordAndReturnVersion(recordId, kind, legalTagName);
  }

  @Test
  public void should_patchRecordData_successfully() {
    HttpResponse<StorageRecord> patchResponse = storageClient.patchRecord(recordId, MERGE_PATCH_CONTENT_TYPE, DATA_PATCH_BODY);
    assertEquals(HttpStatus.SC_OK, patchResponse.statusCode());

    StorageRecord responseJson = patchResponse.body();
    validatePatchResponse(responseJson);
    validateDataFields(responseJson);
  }

  @Test
  public void should_patchRecordTags_successfully() {
    HttpResponse<StorageRecord> patchResponse = storageClient.patchRecord(recordId, MERGE_PATCH_CONTENT_TYPE, TAGS_PATCH_BODY);
    assertEquals(HttpStatus.SC_OK, patchResponse.statusCode());

    StorageRecord responseJson = patchResponse.body();
    assertNotNull(responseJson.tags(), "Response should contain 'tags' field");
    assertEquals("production", responseJson.tags().get("environment"));
    assertEquals("test-project", responseJson.tags().get("project"));
  }

  @Test
  public void should_softDeleteRecord_successfully() {
    HttpResponse<StorageRecord> patchResponse = storageClient.patchRecord(recordId, MERGE_PATCH_CONTENT_TYPE, SOFT_DELETE_PATCH);
    assertEquals(HttpStatus.SC_OK, patchResponse.statusCode());

    assertRecordNotFound(recordId);
  }

  @Test
  public void should_softDeleteAndRecover_successfully() {
    HttpResponse<StorageRecord> deleteResponse = storageClient.patchRecord(recordId, MERGE_PATCH_CONTENT_TYPE, SOFT_DELETE_PATCH);
    assertEquals(HttpStatus.SC_OK, deleteResponse.statusCode());

    assertRecordNotFound(recordId);

    HttpResponse<StorageRecord> recoverResponse = storageClient.patchRecord(recordId, MERGE_PATCH_CONTENT_TYPE, RECOVERY_PATCH);
    assertEquals(HttpStatus.SC_OK, recoverResponse.statusCode());

    HttpResponse<StorageRecord> getRecoveredResponse = storageClient.getRecord(recordId);
    assertEquals(HttpStatus.SC_OK, getRecoveredResponse.statusCode());
  }

  @Test
  public void should_FailOnPatchingSoftDeletedRecord() {
    HttpResponse<StorageRecord> deleteResponse = storageClient.patchRecord(recordId, MERGE_PATCH_CONTENT_TYPE, SOFT_DELETE_PATCH);
    assertEquals(HttpStatus.SC_OK, deleteResponse.statusCode());

    ClientException patchError = assertThrows(ClientException.class,
        () -> storageClient.patchRecord(recordId, MERGE_PATCH_CONTENT_TYPE, TAGS_PATCH_BODY));
    assertEquals(HttpStatus.SC_BAD_REQUEST, patchError.getStatusCode());
  }

  @Test
  public void should_returnBadRequest_whenRecordIdInvalid() {
    String invalidRecordId = "invalid-record-id-format";
    ClientException patchError = assertThrows(ClientException.class,
        () -> storageClient.patchRecord(invalidRecordId, MERGE_PATCH_CONTENT_TYPE, DATA_PATCH_BODY));
    assertEquals(HttpStatus.SC_BAD_REQUEST, patchError.getStatusCode());
  }

  @Test
  public void should_returnNotFound_whenRecordDoesNotExist() {
    String nonExistentRecordId = getTenantId() + ":nonExistent:record"
        + System.currentTimeMillis();
    ClientException patchError = assertThrows(ClientException.class,
        () -> storageClient.patchRecord(nonExistentRecordId, MERGE_PATCH_CONTENT_TYPE, DATA_PATCH_BODY));
    assertEquals(HttpStatus.SC_NOT_FOUND, patchError.getStatusCode());
  }

  @Test
  public void should_returnUnauthorized_whenTokenNotProvided() {
    ClientException patchError = assertThrows(ClientException.class,
        () -> storageClient.patchRecord(recordId, DATA_PATCH_BODY,
            Map.of("Content-Type", MERGE_PATCH_CONTENT_TYPE, "Authorization", "")));
    assertEquals(HttpStatus.SC_UNAUTHORIZED, patchError.getStatusCode());
  }

  @Test
  public void should_returnBadRequest_whenPatchBodyInvalid() {
    ClientException patchError = assertThrows(ClientException.class,
        () -> storageClient.patchRecord(recordId, MERGE_PATCH_CONTENT_TYPE, INVALID_JSON_PATCH));
    assertEquals(HttpStatus.SC_BAD_REQUEST, patchError.getStatusCode());
  }

  private void assertRecordNotFound(String id) {
    ClientException notFound = assertThrows(ClientException.class,
        () -> storageClient.getRecord(id));
    assertEquals(HttpStatus.SC_NOT_FOUND, notFound.getStatusCode());
  }

  private void validatePatchResponse(StorageRecord responseJson) {
    assertNotNull(responseJson, "Response should not be null");
    assertNotNull(responseJson.id(), "Response should contain 'id' field");
    assertNotNull(responseJson.version(), "Response should contain 'version' field");
    assertNotNull(responseJson.data(), "Response should contain 'data' field");
    assertEquals(recordId, responseJson.id(), "StorageRecord ID should match");
  }

  private void validateDataFields(StorageRecord responseJson) {
    Map<String, Object> data = responseJson.data();
    assertNotNull(data, "Data field should not be null");
    assertEquals("Updated Well Name", data.get("wellName"));
    assertEquals("active", data.get("status"));
    assertEquals("newValue", data.get("newField"));
  }
}
