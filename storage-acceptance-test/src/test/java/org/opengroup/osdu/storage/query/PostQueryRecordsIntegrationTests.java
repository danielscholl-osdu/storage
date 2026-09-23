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

import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;

import org.opengroup.osdu.core.test.client.model.storage.QueryRecordsRequest;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.Records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.records.BaseRecordsAcceptanceTest;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class PostQueryRecordsIntegrationTests extends BaseRecordsAcceptanceTest {

  private static final long NOW = System.currentTimeMillis();

  private static String RECORD_ID_PREFIX;
  private static String RECORD_ID;
  private static String KIND;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    RECORD_ID_PREFIX = getTenantId() + ":query:";
    RECORD_ID = RECORD_ID_PREFIX + NOW;
    KIND = getTenantId() + ":ds:query:1.0." + NOW;
    String LEGAL_TAG = getTenantId() + "-storage-" + NOW;

    createLegalTag(LEGAL_TAG);
    StorageRecord[] jsonInput = withTestAcl(RecordUtil.createDefaultRecords(3, RECORD_ID, KIND, LEGAL_TAG));

    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords(jsonInput);
    HttpResponse<CreateRecordsResponse> modifyRecordsResponse = storageClient.putRecords(jsonInput);
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());
    assertEquals(HttpStatus.SC_CREATED, modifyRecordsResponse.statusCode());
  }

  @Test
  public void should_returnSingleRecordMatching_when_givenIdAndNoAttributes() {
    var queryResponse = storageClient.queryRecordsPost(QueryRecordsRequest.of(RECORD_ID + 0));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    Records responseObject = queryResponse.body();
    assertEquals(1, responseObject.records().length);
    assertEquals(0, responseObject.invalidRecords().length);
    assertEquals(0, responseObject.retryRecords().length);

    assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
    assertEquals(RECORD_ID + 0, responseObject.records()[0].id());
    assertEquals(KIND, responseObject.records()[0].kind());
    assertTrue(responseObject.records()[0].createUser() != null && responseObject.records()[0].createTime() != null);
    assertTrue(responseObject.records()[0].modifyUser() != null && responseObject.records()[0].modifyTime() != null);
    assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
    assertEquals(3, responseObject.records()[0].data().size());
  }

  @Test
  public void should_returnOnlyRequestedDataProperties_when_specificAttributesAreGiven() {
    var queryResponse = storageClient.queryRecordsPost(QueryRecordsRequest.withAttributes(new String[] {"data.count"}, RECORD_ID + 1));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    Records responseObject = queryResponse.body();
    assertEquals(1, responseObject.records()[0].data().size());
    assertEquals(123456789L, responseObject.records()[0].data().get("count"));
  }

  @Test
  public void should_returnMultipleRecordsMatchingGivenIds_when_noAttributesAreGiven() {
    var queryResponse = storageClient.queryRecordsPost(QueryRecordsRequest.of(RECORD_ID + 0, RECORD_ID + 1, RECORD_ID + 2));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    Records responseObject = queryResponse.body();
    assertEquals(3, responseObject.records().length);
    assertEquals(0, responseObject.invalidRecords().length);
    assertEquals(0, responseObject.retryRecords().length);

    String[] ids = ArrayUtils.addAll(new String[] {responseObject.records()[0].id()},
        responseObject.records()[1].id(), responseObject.records()[2].id());
    assertTrue(Arrays.asList(ids).contains(RECORD_ID + 0));
    assertTrue(Arrays.asList(ids).contains(RECORD_ID + 1));
    assertTrue(Arrays.asList(ids).contains(RECORD_ID + 2));
  }

  @Test
  public void should_returnInvalidRecord_when_nonExistingIDGiven() {
    String notExistingId = RECORD_ID_PREFIX + "nonexisting:id";
    var queryResponse = storageClient.queryRecordsPost(QueryRecordsRequest.of(notExistingId));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    Records responseObject = queryResponse.body();
    assertEquals(0, responseObject.records().length);
    assertEquals(1, responseObject.invalidRecords().length);
    assertEquals(notExistingId, responseObject.invalidRecords()[0]);
    assertEquals(0, responseObject.retryRecords().length);
  }

}
