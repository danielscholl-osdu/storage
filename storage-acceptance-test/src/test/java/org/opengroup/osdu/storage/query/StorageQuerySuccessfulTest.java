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

package org.opengroup.osdu.storage.query;

import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.QueryRecordsRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class StorageQuerySuccessfulTest extends BaseStorageAcceptanceTest {

  private String KIND_ONE;
  private String KIND_ID_ONE;
  private String LEGAL_TAG_NAME;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    KIND_ONE = getTenantId() + ":test:endtoend:1.1." + now;
    KIND_ID_ONE = getTenantId() + ":endtoend:1.1." + now;
    LEGAL_TAG_NAME = getTenantId() + "-storage-" + now;

    createLegalTag(LEGAL_TAG_NAME);
  }

  @Test
  public void should_retrieveAllKinds_when_toCursorIdIsGiven() {
    if (configUtils.getIsSchemaEndpointsEnabled()) {
      var recordResponse = storageClient.queryKindsGet("?limit=10");
      assertEquals(HttpStatus.SC_OK, recordResponse.statusCode());
      String cursorValue = recordResponse.body().cursor();
      assertEquals(cursorValue, recordResponse.body().cursor());
      var recordResponseWithCursorValue = storageClient.queryKindsGet(
          "?cursor=" + cursorValue + "&limit=10");
      assertEquals(HttpStatus.SC_OK, recordResponseWithCursorValue.statusCode());
    }
  }

  @Test
  public void should_retrieveAllRecords_when_kindIsGiven() {
    var recordResponse = createTestRecord(KIND_ONE, KIND_ID_ONE, LEGAL_TAG_NAME);
    assertEquals(HttpStatus.SC_CREATED, recordResponse.statusCode());
    var recordResponseGet = storageClient.queryRecordsGet("?kind=" + KIND_ONE);
    assertEquals(HttpStatus.SC_OK, recordResponseGet.statusCode());
  }

  @Test
  public void should_queryToFetchMultipleRecords_when_recordIsGiven() {
    var recordResponse = createTestRecord(KIND_ONE, KIND_ID_ONE, LEGAL_TAG_NAME);
    assertEquals(HttpStatus.SC_CREATED, recordResponse.statusCode());
    CreateRecordsResponse recordResult = recordResponse.body();
    QueryRecordsRequest createSearchRecordPayload = QueryRecordsRequest.withAttributes(
        new String[] {""}, recordResult.recordIds()[0]);
    var recordResponsePost = storageClient.queryRecordsPost(createSearchRecordPayload);
    assertEquals(HttpStatus.SC_OK, recordResponsePost.statusCode());
  }

  @Test
  public void should_queryToFetchMultipleRecords_when_recordIsGiven_and_trailingSlash() {
    var recordResponse = createTestRecord(KIND_ONE, KIND_ID_ONE, LEGAL_TAG_NAME);
    assertEquals(HttpStatus.SC_CREATED, recordResponse.statusCode());
    CreateRecordsResponse recordResult = recordResponse.body();
    QueryRecordsRequest createSearchRecordPayload = QueryRecordsRequest.withAttributes(
        new String[] {""}, recordResult.recordIds()[0]);
    var recordResponsePost = storageClient.queryRecordsPost(createSearchRecordPayload);
    assertEquals(HttpStatus.SC_OK, recordResponsePost.statusCode());
  }

  private HttpResponse<CreateRecordsResponse> createTestRecord(
      String kind, String id, String legalName) {
    StorageRecord[] jsonInputRecord = RecordUtil.createDefaultRecords(id, kind, legalName);
    return storageClient.putRecords(jsonInputRecord);
  }

}
