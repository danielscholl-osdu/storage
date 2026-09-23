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

import org.junit.jupiter.api.Assertions;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.StorageClient;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.MultiRecordHeadersInfo;
import org.opengroup.osdu.core.test.client.model.storage.MultiRecordHeadersRequest;
import org.opengroup.osdu.core.test.client.model.storage.QueryRecordsRequest;
import org.opengroup.osdu.core.test.client.model.storage.RecordVersions;
import org.opengroup.osdu.core.test.client.model.storage.Records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class RecordAccessAuthorizationTests extends BaseRecordsAcceptanceTest {

  private final StorageClient storageClientNoAccess;

  private String LEGAL_TAG;
  private String KIND;
  private String RECORD_ID;

  RecordAccessAuthorizationTests() {
    super(List.of(UserType.PRIVILEGED_USER, UserType.NO_ACCESS_USER));
    this.storageClientNoAccess = new StorageClient(this.stringHttpClient, UserType.NO_ACCESS_USER);
  }

  @AfterEach
  @Override
  protected void teardown() {
    storageClientNoAccess.teardown();
    super.teardown();
  }

  @BeforeEach
  @Override
  protected void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    LEGAL_TAG = getTenantId() + "-storage-" + now;
    KIND = getTenantId() + ":dataaccess:no:1.1." + now;
    RECORD_ID = getTenantId() + ":no:1.1." + now;

    createLegalTag(LEGAL_TAG);
    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords(
        withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID, KIND, LEGAL_TAG)));
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());
  }

  @Test
  public void should_receiveHttp403_when_userIsNotAuthorizedToGetLatestVersionOfARecord() {
    assertNotAuthorized(() -> storageClientNoAccess.getRecord(RECORD_ID));
  }

  @Test
  public void should_receiveHttp403_when_userIsNotAuthorizedToListVersionsOfARecord() {
    assertNotAuthorized(() -> storageClientNoAccess.getRecordVersions(RECORD_ID));
  }

  @Test
  public void should_receiveHttp403_when_userIsNotAuthorizedToGetSpecificVersionOfARecord() {
    var versionsResponse = storageClient.getRecordVersions(RECORD_ID);
    assertEquals(HttpStatus.SC_OK, versionsResponse.statusCode());
    RecordVersions versions = versionsResponse.body();
    String version = versions.versions()[0].toString();

    assertNotAuthorized(
        () -> storageClientNoAccess.getRecordVersion(RECORD_ID, version));
  }

  @Test
  public void should_receiveHttp403_when_userIsNotAuthorizedToDeleteRecord() {
    assertNotAuthorized(() -> storageClientNoAccess.softDeleteRecords(RECORD_ID + ":delete",
        Map.of("anything", "anything")));
  }

  @Test
  public void should_receiveHttp403_when_userIsNotAuthorizedToPurgeRecord() {
    assertNotAuthorized(() -> storageClientNoAccess.deleteRecord(RECORD_ID));
  }

  @Test
  public void should_receiveHttp403_when_userIsNotAuthorizedToUpdateARecord() {
    assertNotAuthorized(() -> storageClientNoAccess.putRecords(
        withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID, KIND, LEGAL_TAG))));
  }

  @Test
  public void should_NoneRecords_when_fetchingMultipleRecords_and_notAuthorizedToRecords() {
    String newRecordId = getTenantId() + ":no:2.2." + System.currentTimeMillis();

    HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(
        withTestAcl(RecordUtil.createDefaultRecords(newRecordId, KIND, LEGAL_TAG)));
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

    var queryResponse = storageClient.queryRecordsPost(
        QueryRecordsRequest.of(RECORD_ID, newRecordId));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());

    Records responseObject = queryResponse.body();
    assertEquals(2, responseObject.records().length);
    assertEquals(0, responseObject.invalidRecords().length);
    assertEquals(0, responseObject.retryRecords().length);

    assertNotAuthorized(() -> storageClientNoAccess.getRecord(newRecordId));
  }

  @Test
  public void should_notReturnRecordHeaders_when_userIsNotAuthorizedToRecord() {
    var queryResponse = storageClientNoAccess.queryRecordsHeadersPost(
        MultiRecordHeadersRequest.of(RECORD_ID));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());

    MultiRecordHeadersInfo responseObject = queryResponse.body();
    Assertions.assertNotNull(responseObject);
    assertEquals(0, responseObject.records().length);
    assertEquals(1, responseObject.notFound().length);
    assertEquals(RECORD_ID, responseObject.notFound()[0]);
  }

  @Test
  public void should_notReturnRecordHeadersAndReportNotFound_when_userIsNotAuthorizedAndNonExistentAndInvalidIdsGiven() {
    String invalidId = "invalid_id_format";
    String nonExistingId = getTenantId() + ":no:nonexisting:" + System.currentTimeMillis();

    var queryResponse = storageClientNoAccess.queryRecordsHeadersPost(
        MultiRecordHeadersRequest.of(RECORD_ID, nonExistingId, invalidId));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());

    MultiRecordHeadersInfo responseObject = queryResponse.body();
    Assertions.assertNotNull(responseObject);
    assertEquals(0, responseObject.records().length);

    // Both the unauthorized ID and the non-existing ID should be reported as not found to protect privacy
    assertEquals(2, responseObject.notFound().length);
    List<String> notFoundList = java.util.Arrays.asList(responseObject.notFound());
    Assertions.assertTrue(notFoundList.contains(RECORD_ID));
    Assertions.assertTrue(notFoundList.contains(nonExistingId));

    // The invalid ID should be reported as invalid
    assertEquals(1, responseObject.invalidRecords().length);
    assertEquals(invalidId, responseObject.invalidRecords()[0]);
  }

  private void assertNotAuthorized(Executable call) {
    ClientException ex = assertThrows(ClientException.class, call);
    assertEquals(HttpStatus.SC_FORBIDDEN, ex.getStatusCode());
    AppError error = ex.getError();
    assertEquals(HttpStatus.SC_FORBIDDEN, error.getCode());
    assertEquals("Access denied", error.getReason());
    assertEquals("The user is not authorized to perform this action", error.getMessage());
  }
}
