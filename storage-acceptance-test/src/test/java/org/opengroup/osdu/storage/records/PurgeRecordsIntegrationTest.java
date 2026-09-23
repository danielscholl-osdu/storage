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
import org.opengroup.osdu.core.test.client.model.storage.RecordVersions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class PurgeRecordsIntegrationTest extends BaseRecordsAcceptanceTest {

  private String RECORD_ID;
  private String RECORD_ID1;
  private String RECORD_ID2;
  private String RECORD_ID3;
  private String RECORD_ID4;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    RECORD_ID = getTenantId() + ":getrecord:" + now;
    RECORD_ID1 = getTenantId() + ":getrecord1:" + now;
    RECORD_ID2 = getTenantId() + ":getrecord2:" + now;
    RECORD_ID3 = getTenantId() + ":getrecord3:" + now;
    RECORD_ID4 = getTenantId() + ":getrecord4:" + now;
    String KIND = getTenantId() + ":ds:getrecord:1.0." + now;
    String LEGAL_TAG = getTenantId() + "-storage-" + now;

    createLegalTag(LEGAL_TAG);
    StorageRecord[] jsonInput = withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID, KIND, LEGAL_TAG));
    StorageRecord[] jsonInput1 = withTestAcl(
        RecordUtil.createDefaultRecords(RECORD_ID1, KIND, LEGAL_TAG));
    StorageRecord[] jsonInput2 = withTestAcl(
        RecordUtil.createDefaultRecords(RECORD_ID2, KIND, LEGAL_TAG));
    StorageRecord[] jsonInput3 = withTestAcl(
        RecordUtil.createDefaultRecords(RECORD_ID3, KIND, LEGAL_TAG));
    StorageRecord[] jsonInput4 = withTestAcl(
        RecordUtil.createDefaultRecords(RECORD_ID4, KIND, LEGAL_TAG));

    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords(jsonInput);
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());

    for (int i = 0; i < 4; i++) {
      HttpResponse<CreateRecordsResponse> response1 = storageClient.putRecords(jsonInput1);
      assertEquals(HttpStatus.SC_CREATED, response1.statusCode());

      HttpResponse<CreateRecordsResponse> response2 = storageClient.putRecords(jsonInput2);
      assertEquals(HttpStatus.SC_CREATED, response2.statusCode());

      HttpResponse<CreateRecordsResponse> response3 = storageClient.putRecords(jsonInput3);
      assertEquals(HttpStatus.SC_CREATED, response3.statusCode());

      HttpResponse<CreateRecordsResponse> response4 = storageClient.putRecords(jsonInput4);
      assertEquals(HttpStatus.SC_CREATED, response4.statusCode());
    }
  }

  @Test
  public void should_ReturnHttp204_when_purgingRecordSuccessfully() {
    HttpResponse<Void> deleteResponse = storageClient.deleteRecord(RECORD_ID);
    assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());

    ClientException notFound = assertThrows(ClientException.class,
        () -> storageClient.getRecord(RECORD_ID));
    assertEquals(HttpStatus.SC_NOT_FOUND, notFound.getStatusCode());
  }

  @Test
  public void shouldReturnHttp204_whenPurgeRecordVersions_byLimit_isSuccess() {
    String queryParams = "?limit=2";
    HttpResponse<Void> response = storageClient.purgeRecordVersions(RECORD_ID1, queryParams);
    assertEquals(HttpStatus.SC_NO_CONTENT, response.statusCode());

    var versionsResponse = storageClient.getRecordVersions(RECORD_ID1);

    assertEquals(HttpStatus.SC_OK, versionsResponse.statusCode());

    RecordVersions json = versionsResponse.body();
    assertEquals(RECORD_ID1, json.recordId());
    assertEquals(2, json.versions().length);
  }

  @Test
  public void shouldReturnHttp204_whenPurgeRecordVersions_byVersionIds_isSuccess() {
    var versionsResponse = storageClient.getRecordVersions(RECORD_ID2);
    assertEquals(HttpStatus.SC_OK, versionsResponse.statusCode());
    RecordVersions json = versionsResponse.body();
    String versionIds = json.versions()[0] + "," + json.versions()[1];

    String queryParams = "?limit=2&versionIds=" + versionIds;
    HttpResponse<Void> response = storageClient.purgeRecordVersions(RECORD_ID2, queryParams);
    assertEquals(HttpStatus.SC_NO_CONTENT, response.statusCode());

    var versionsHttpResponse2 = storageClient.getRecordVersions(RECORD_ID2);
    assertEquals(HttpStatus.SC_OK, versionsHttpResponse2.statusCode());
    RecordVersions json1 = versionsHttpResponse2.body();
    assertEquals(RECORD_ID2, json1.recordId());
    assertEquals(2, json1.versions().length);
  }

  @Test
  public void shouldReturnHttp204_whenPurgeRecordVersions_byFromVersion_isSuccess() {
    var versionsResponse = storageClient.getRecordVersions(RECORD_ID3);
    assertEquals(HttpStatus.SC_OK, versionsResponse.statusCode());
    RecordVersions json = versionsResponse.body();
    String fromVersion = json.versions()[1].toString();

    String queryParams = "?from=" + fromVersion;
    HttpResponse<Void> response = storageClient.purgeRecordVersions(RECORD_ID3, queryParams);
    assertEquals(HttpStatus.SC_NO_CONTENT, response.statusCode());

    var versionsHttpResponse2 = storageClient.getRecordVersions(RECORD_ID3);
    assertEquals(HttpStatus.SC_OK, versionsHttpResponse2.statusCode());
    RecordVersions json1 = versionsHttpResponse2.body();
    assertEquals(RECORD_ID3, json1.recordId());
    assertEquals(2, json1.versions().length);
  }

  @Test
  public void shouldReturnHttp400BadRequest_whenPurgeRecordVersions_forInvalidVersionIds() {
    Long versionId1 = 404L;
    long versionId2 = 405L;
    String invalidVersionIds = versionId1 + "," + versionId2;
    String queryParams = "?versionIds=" + invalidVersionIds;

    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.purgeRecordVersions(RECORD_ID4, queryParams));
    String errorMessage = String.format(
        "Invalid Version Ids. The versionIds contains non existing version(s) '%s'",
        invalidVersionIds);

    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getError().getCode());
    assertEquals(errorMessage, ex.getError().getMessage());
  }

  @Test
  public void shouldReturnHttp400BadRequest_whenPurgeRecordVersions_forLimitExceedsRecordVersions() {
    int totalVersions = 4;
    int limitValue = 5;
    String queryParams = "?limit=" + limitValue;

    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.purgeRecordVersions(RECORD_ID4, queryParams));
    String errorMessage = String.format(
        "The record '%s' version count (excluding latest version) is : %d , which is less than limit value : %d ",
        RECORD_ID4, totalVersions - 1, limitValue);

    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("Invalid limit.", ex.getError().getReason());
    assertEquals(errorMessage, ex.getError().getMessage());
  }

  @Test
  public void shouldReturnHttp400BadRequest_whenPurgeRecordVersions_forInvalidFromVersion() {
    Long fromVersion = 404L;
    String queryParams = "?from=" + fromVersion;

    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.purgeRecordVersions(RECORD_ID4, queryParams));
    String errorMessage = String.format(
        "Invalid 'from' version. The record version does not contains specified from version '%d'",
        fromVersion);

    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("Invalid 'from' version.", ex.getError().getReason());
    assertEquals(errorMessage, ex.getError().getMessage());
  }

  @Test
  public void shouldReturnHttp400BadRequest_whenPurgeRecordVersions_forInvalidLimitAndValidFromVersion() {
    var versionsResponse = storageClient.getRecordVersions(RECORD_ID4);
    assertEquals(HttpStatus.SC_OK, versionsResponse.statusCode());
    RecordVersions json = versionsResponse.body();
    Long fromVersion = json.versions()[1];

    int limitValue = 3;
    String queryParams = "?limit=" + limitValue + "&from=" + fromVersion;

    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.purgeRecordVersions(RECORD_ID4, queryParams));
    String errorMessage = String.format(
        "Invalid limit. Given limit count %d, exceeds the record versions count specified by the given 'from' version '%d'",
        limitValue, fromVersion);

    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("Invalid limit.", ex.getError().getReason());
    assertEquals(errorMessage, ex.getError().getMessage());
  }
}
