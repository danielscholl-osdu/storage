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
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;

import org.opengroup.osdu.core.test.client.model.storage.QueryRecordsRequest;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.Records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class RecordWithNullFieldTest extends BaseRecordsAcceptanceTest {

  private String LEGAL_TAG;
  private String KIND;
  private String RECORD_ID;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    LEGAL_TAG = getTenantId() + "-storage-" + now;
    KIND = getTenantId() + ":test:endtoend:1.1." + now;
    RECORD_ID = getTenantId() + ":endtoend:1.1." + now;
    createLegalTag(LEGAL_TAG);
  }

  @Test
  public void should_returnRecordWithoutNullFields_when_recordIsIngestedWithNullFields() {
    HttpResponse<CreateRecordsResponse> response = storageClient.putRecordsSerializingNulls(
        withTestAcl(RecordUtil.createRecordsWithData(RECORD_ID, KIND, LEGAL_TAG, null)));
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());

    var getResponse = storageClient.getRecord(RECORD_ID);

    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());

    StorageRecord record = getResponse.body();
    assertEquals(58377304471659395L, record.data().get("score-int"));
    assertEquals(58377304.471659395, record.data().get("score-double"));
    assertNullDataField(record.data(), "custom");

    var queryResponse = storageClient.queryRecordsPost(QueryRecordsRequest.withAttributes(new String[0], RECORD_ID));

    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());

    Records responseObject = queryResponse.body();
    assertEquals(1, responseObject.records().length);
    assertEquals(0, responseObject.invalidRecords().length);
    assertEquals(0, responseObject.retryRecords().length);

    StorageRecord result = responseObject.records()[0];

    assertEquals(58377304471659395L, result.data().get("score-int"));
    assertEquals(58377304.471659395, result.data().get("score-double"));
    assertNullDataField(result.data(), "custom");

    queryResponse = storageClient.queryRecordsPost(
        QueryRecordsRequest.withAttributes(new String[] {"data.custom"}, RECORD_ID));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    responseObject = queryResponse.body();

    assertEquals(1, responseObject.records().length);
    assertEquals(0, responseObject.invalidRecords().length);
    assertEquals(0, responseObject.retryRecords().length);

    result = responseObject.records()[0];

    assertFalse(result.data().containsKey("score-int"));
    assertFalse(result.data().containsKey("score-double"));
    assertNullDataField(result.data(), "custom");
  }

  private static void assertNullDataField(java.util.Map<String, Object> data, String field) {
    assertNotNull(data);
    assertTrue(data.containsKey(field), "expected data field '" + field + "' to be present");
    assertNull(data.get(field));
  }
}
