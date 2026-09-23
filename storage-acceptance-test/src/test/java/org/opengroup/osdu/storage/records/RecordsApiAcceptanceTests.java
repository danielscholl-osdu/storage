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

import org.opengroup.osdu.core.test.client.HttpResponse;

import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.RecordAcl;
import org.opengroup.osdu.core.test.client.model.storage.RecordLegal;
import org.opengroup.osdu.core.test.client.model.storage.RecordVersions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Strings;

import java.util.Arrays;
import java.util.Map;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.TestUtils;

public final class RecordsApiAcceptanceTests extends BaseRecordsAcceptanceTest {

  private String recordId;
  private String recordNewId;
  private String recordId3;
  private String kind;
  private String kindWithOtherTenant;
  private String legalTag;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    recordId = getTenantId() + ":inttest:" + now;
    recordNewId = getTenantId() + ":inttest:" + (now + 1);
    recordId3 = getTenantId() + ":inttest:testModifyTimeUser-" + now;
    kind = getTenantId() + ":ds:inttest:1.0." + now;
    kindWithOtherTenant = "tenant1:ds:inttest:1.0." + now;
    legalTag = createLegalTagName("");

    createLegalTag(legalTag);
    storageClient.putRecords(createRecordsBody(recordId, "tian"));
  }

  @Test
  public void should_createNewRecord_when_givenValidRecord_and_verifyNoAncestry() {
    StorageRecord[] records = createRecordsBody(recordNewId, "Flor\u00e9");

    var createResponse = storageClient.putRecords(records);

    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

    CreateRecordsResponse result = createResponse.body();
    assertEquals(1, result.recordCount());
    assertEquals(1, result.recordIds().length);
    assertEquals(1, result.recordIdVersions().length);
    assertEquals(recordNewId, result.recordIds()[0]);

    HttpResponse<StorageRecord> response = storageClient.getRecord(recordNewId);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord recordResult = response.body();
    assertEquals("Flor\u00e9", recordResult.data().get("name"));
    assertNull(recordResult.data().get("ancestry"));
  }

  @Test
  public void should_updateRecordsWithSameData_when_skipDupesIsFalse() {
    HttpResponse<StorageRecord> response = storageClient.getRecord(recordId);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord recordResult = response.body();
    long initialVersion = versionAsLong(recordResult);

    StorageRecord[] records = createRecordsBody(recordId, "tianNew");

    HttpResponse<CreateRecordsResponse> putResponse = storageClient.putRecords("?skipdupes=true", records);
    assertEquals(HttpStatus.SC_CREATED, putResponse.statusCode());
    CreateRecordsResponse result = putResponse.body();
    assertNotNull(result);
    assertEquals(1, result.recordCount());
    assertEquals(1, result.recordIds().length);
    assertEquals(1, result.recordIdVersions().length);
    assertEquals(0, skippedCount(result));
    assertEquals(recordId, result.recordIds()[0]);

    response = storageClient.getRecord(recordId);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord recordResult2 = response.body();
    assertNotEquals(initialVersion, versionAsLong(recordResult2));
    assertEquals("tianNew", recordResult2.data().get("name"));

    putResponse = storageClient.putRecords("?skipdupes=true", records);
    assertEquals(HttpStatus.SC_CREATED, putResponse.statusCode());
    result = putResponse.body();
    assertNotNull(result);
    assertEquals(1, result.recordCount());
    assertNull(result.recordIds());
    assertNull(result.recordIdVersions());
    assertEquals(1, skippedCount(result),
        "Expected to skip the update when the data was the same as previous update and skipdupes is true");
    assertEquals(recordId, result.skippedRecordIds()[0]);

    response = storageClient.getRecord(recordId);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord recordResult3 = response.body();
    assertEquals(versionAsLong(recordResult2), versionAsLong(recordResult3));
    assertEquals("tianNew", recordResult3.data().get("name"));

    putResponse = storageClient.putRecords("?skipdupes=false", records);
    assertEquals(HttpStatus.SC_CREATED, putResponse.statusCode());
    result = putResponse.body();
    assertNotNull(result);
    assertEquals(1, result.recordCount());
    assertEquals(1, result.recordIds().length);
    assertEquals(1, result.recordIdVersions().length);
    assertEquals(0, skippedCount(result),
        "Expected to NOT skip the update when data is the same but skipdupes is false");
    assertEquals(recordId, result.recordIds()[0]);

    response = storageClient.getRecord(recordId);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    recordResult3 = response.body();
    assertNotEquals(versionAsLong(recordResult2), versionAsLong(recordResult3));
    assertEquals("tianNew", recordResult3.data().get("name"));
  }

  @Test
  public void should_getAnOlderVersion_and_theMostRecentVersion_and_retrieveAllVersions() {
    HttpResponse<StorageRecord> response = storageClient.getRecord(recordId);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord originalRecordResult = response.body();
    long originalVersion = versionAsLong(originalRecordResult);

    StorageRecord[] records = createRecordsBody(recordId, "tianNew2");
    HttpResponse<CreateRecordsResponse> putResponse = storageClient.putRecords(records);
    assertEquals(HttpStatus.SC_CREATED, putResponse.statusCode());

    response = storageClient.getRecordVersion(recordId, originalRecordResult.version());
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord recordResultVersion = response.body();
    assertEquals(originalRecordResult.id(), recordResultVersion.id());
    assertEquals(originalRecordResult.version(), recordResultVersion.version());
    assertEquals(originalRecordResult.data().get("name"), recordResultVersion.data().get("name"));

    response = storageClient.getRecord(recordId);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord newRecordResult = response.body();
    assertEquals(originalRecordResult.id(), newRecordResult.id());
    assertNotEquals(originalVersion, versionAsLong(newRecordResult));
    assertEquals("tianNew2", newRecordResult.data().get("name"));

    var versionsHttpResponse = storageClient.getRecordVersions(recordId);
    assertEquals(HttpStatus.SC_OK, versionsHttpResponse.statusCode());
    RecordVersions versionsResponse = versionsHttpResponse.body();
    assertEquals(recordId, versionsResponse.recordId());
    List<Long> versions = Arrays.asList(versionsResponse.versions());
    assertTrue(versions.contains(originalVersion));
    assertTrue(versions.contains(versionAsLong(newRecordResult)));
  }

  @Test
  public void should_deleteAllVersionsOfARecord_when_deletingARecordById() {
    String idToDelete = recordId + 1;
    StorageRecord[] records = createRecordsBody(idToDelete, "tianNew2");

    var createResponse = storageClient.putRecords(records);

    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    HttpResponse<Void> response = storageClient.deleteRecord(idToDelete);
    assertEquals(HttpStatus.SC_NO_CONTENT, response.statusCode());

    ClientException notFound = assertThrows(ClientException.class,
        () -> storageClient.getRecord(idToDelete));
    assertEquals(404, notFound.getStatusCode());
    assertEquals(404, notFound.getError().getCode());
    assertEquals("Record not found", notFound.getError().getReason());
    assertEquals("The record '" + idToDelete + "' was not found", notFound.getError().getMessage());
  }

  @Test
  public void should_ingestRecord_when_noRecordIdIsProvided() {
    StorageRecord[] body = createRecordsBody(null, "Foo");

    var createResponse = storageClient.putRecords(body);

    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

    CreateRecordsResponse responseJson = createResponse.body();
    assertEquals(1, responseJson.recordCount());
    assertEquals(1, responseJson.recordIds().length);
    assertTrue(responseJson.recordIds()[0].startsWith(getTenantId() + ":"));
  }

  @Test
  public void should_returnWholeRecord_when_recordIsIngestedWithAllFields() {
    final String wholeRecordId = getTenantId() + ":inttest:wholerecord-"
        + System.currentTimeMillis();
    StorageRecord[] body = createRecordsBody(wholeRecordId, "Foo");

    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords(body);
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());

    var getResponse = storageClient.getRecord(wholeRecordId);

    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());

    StorageRecord responseJson = getResponse.body();
    assertEquals(wholeRecordId, responseJson.id());
    assertEquals(kind, responseJson.kind());
    assertEquals(getAcl(), responseJson.acl().owners()[0]);
    assertEquals(getAcl(), responseJson.acl().viewers()[0]);
    assertEquals("Foo", responseJson.data().get("name"));

    storageClient.deleteRecord(wholeRecordId);
  }

  @Test
  public void should_returnWholeRecord_when_recordIsIngestedWithOtherTenantInKind() {
    final String wholeRecordId = getTenantId() + ":inttest:wholerecord-"
        + System.currentTimeMillis();
    StorageRecord[] body = createRecordsBody(wholeRecordId, "Foo", kindWithOtherTenant);
    var createResponse = storageClient.putRecords(body);
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    var getResponse = storageClient.getRecord(wholeRecordId);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    StorageRecord responseJson = getResponse.body();
    assertEquals(wholeRecordId, responseJson.id());
    assertEquals(kindWithOtherTenant, responseJson.kind());
    assertEquals(getAcl(), responseJson.acl().owners()[0]);
    assertEquals(getAcl(), responseJson.acl().viewers()[0]);
    assertEquals("Foo", responseJson.data().get("name"));

    storageClient.deleteRecord(wholeRecordId);
  }

  @Test
  public void should_insertNewRecord_when_skipDupesIsTrue() {
    final String wholeRecordId = getTenantId() + ":inttest:wholerecord-"
        + System.currentTimeMillis();
    StorageRecord[] body = createRecordsBody(wholeRecordId, "Foo");
    var createResponse = storageClient.putRecords("?skipdupes=true", body);
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    CreateRecordsResponse result = createResponse.body();
    assertNotNull(result);
    assertEquals(1, result.recordCount());
    assertEquals(1, result.recordIds().length,
        "Expected to insert the new record when skipdupes is true");
    assertEquals(1, result.recordIdVersions().length);
    assertEquals(wholeRecordId, result.recordIds()[0]);
    HttpResponse<Void> response = storageClient.deleteRecord(wholeRecordId);
    assertEquals(HttpStatus.SC_NO_CONTENT, response.statusCode());
  }

  @Test
  public void should_createNewRecord_withSpecialCharacter_ifEnabled() {
    final long currentTimeMillis = System.currentTimeMillis();
    final String specialRecordId = getTenantId()
        + ":inttest:testSpecialChars%abc%2Ffoobar-" + currentTimeMillis;
    final String encodedRecordId = getTenantId()
        + ":inttest:testSpecialChars%25abc%252Ffoobar-" + currentTimeMillis;

    StorageRecord[] records = createRecordsBody(specialRecordId, "TestSpecialCharacters");

    var createResponse = storageClient.putRecords(records);

    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

    CreateRecordsResponse result = createResponse.body();
    assertEquals(1, result.recordCount());
    assertEquals(1, result.recordIds().length);
    assertEquals(1, result.recordIdVersions().length);
    assertEquals(specialRecordId, result.recordIds()[0]);

    HttpResponse<StorageRecord> response = storageClient.getRecord(encodedRecordId);

    if (configUtils.getBooleanProperty("enableEncodedSpecialCharactersInURL", "false")) {
      assertEquals(HttpStatus.SC_OK, response.statusCode());
      StorageRecord recordResult = response.body();
      assertEquals("TestSpecialCharacters", recordResult.data().get("name"));
    } else {
      assertNotEquals(HttpStatus.SC_OK, response.statusCode());
    }

    storageClient.deleteRecord(encodedRecordId);
  }

  @Test
  public void should_updateModifyTimeWithRecordUpdate() {
    StorageRecord[] records = createRecordsBody(recordId3, "tianNew");

    var createResponse = storageClient.putRecords("?skipdupes=false", records);

    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

    CreateRecordsResponse result = createResponse.body();
    assertNotNull(result);
    assertEquals(recordId3, result.recordIds()[0]);
    String firstVersionNumber = StringUtils.substringAfterLast(result.recordIdVersions()[0], ":");

    HttpResponse<StorageRecord> response = storageClient.getRecord(recordId3);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord recordResult1 = response.body();
    assertNull(recordResult1.modifyTime());
    assertNull(recordResult1.modifyUser());

    HttpResponse<CreateRecordsResponse> putResponse = storageClient.putRecords("?skipdupes=false", records);
    assertEquals(HttpStatus.SC_CREATED, putResponse.statusCode());
    CreateRecordsResponse result2 = putResponse.body();
    assertNotNull(result2);
    String secondVersionNumber = StringUtils.substringAfterLast(result2.recordIdVersions()[0], ":");

    putResponse = storageClient.putRecords("?skipdupes=false", records);
    assertEquals(HttpStatus.SC_CREATED, putResponse.statusCode());
    CreateRecordsResponse result3 = putResponse.body();
    assertNotNull(result3);
    String thirdLastVersionNumber = StringUtils.substringAfterLast(result3.recordIdVersions()[0],
        ":");

    response = storageClient.getRecordVersion(recordId3, firstVersionNumber);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord recordResult2 = response.body();
    assertNull(recordResult2.modifyTime());
    assertNull(recordResult2.modifyUser());

    response = storageClient.getRecordVersion(recordId3, secondVersionNumber);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord recordResult3 = response.body();

    response = storageClient.getRecordVersion(recordId3, thirdLastVersionNumber);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord recordResult4 = response.body();

    assertNotEquals(recordResult4.modifyTime(), recordResult3.modifyTime());
  }

  private StorageRecord[] createRecordsBody(String id, String name) {
    return withTestAcl(new StorageRecord[] {singleEntityRecord(id, name, kind, legalTag)});
  }

  private StorageRecord[] createRecordsBody(String id, String name, String recordKind) {
    return withTestAcl(new StorageRecord[] {singleEntityRecord(id, name, recordKind, legalTag)});
  }

  private static int skippedCount(CreateRecordsResponse result) {
    return result.skippedRecordIds() == null ? 0 : result.skippedRecordIds().length;
  }

  private static long versionAsLong(StorageRecord record) {
    return Long.parseLong(record.version());
  }

  public static StorageRecord singleEntityRecord(String id, String name, String recordKind,
      String legalTagName) {
    String recordId = Strings.isNullOrEmpty(id) ? null : id;
    RecordAcl acl = new RecordAcl(
        new String[] {TestUtils.getAcl()}, new String[] {TestUtils.getAcl()});
    RecordLegal legal = new RecordLegal(new String[] {legalTagName}, new String[] {"BR"});
    return new StorageRecord(recordId, null, recordKind, acl, Map.of("name", name), legal, null, null,
        null, null, null, null, null);
  }
}
