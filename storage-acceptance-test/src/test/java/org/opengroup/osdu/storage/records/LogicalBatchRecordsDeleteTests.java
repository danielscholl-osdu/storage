// Copyright 2017-2021, Schlumberger
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

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.hc.core5.http.HttpStatus.SC_MULTI_STATUS;
import static org.apache.hc.core5.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.Lists;
import java.util.Map;
import org.opengroup.osdu.core.test.client.model.storage.BatchDeleteRecordError;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.RecordAcl;
import org.opengroup.osdu.core.test.client.model.storage.RecordLegal;

import java.util.List;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.ClientException;

public final class LogicalBatchRecordsDeleteTests extends BaseRecordsAcceptanceTest {

  private String KIND;
  private String RECORD_ID_1;
  private String RECORD_ID_2;
  private String NOT_EXISTED_RECORD_ID;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    String LEGAL_TAG = getTenantId() + "-storage-" + now;
    KIND = getTenantId() + ":delete:inttest:1.0." + now;
    RECORD_ID_1 = getTenantId() + ":testint:" + now;
    RECORD_ID_2 = getTenantId() + ":testint2:" + now;
    NOT_EXISTED_RECORD_ID = getTenantId() + ":notexisted:" + now;

    createLegalTag(LEGAL_TAG);

    StorageRecord[] firstBody = createRecords(RECORD_ID_1, "anything", Lists.newArrayList(LEGAL_TAG),
        Lists.newArrayList("BR", "IT"));
    StorageRecord[] secondBody = createRecords(RECORD_ID_2, "anything", Lists.newArrayList(LEGAL_TAG),
        Lists.newArrayList("BR", "IT"));

    HttpResponse<CreateRecordsResponse> firstResponse = storageClient.putRecords(firstBody);
    HttpResponse<CreateRecordsResponse> secondResponse = storageClient.putRecords(secondBody);

    assertEquals(HttpStatus.SC_CREATED, firstResponse.statusCode());
    assertEquals(HttpStatus.SC_CREATED, secondResponse.statusCode());
  }

  @Test
  public void should_deleteRecordsLogically_successfully() {
    HttpResponse<BatchDeleteRecordError[]> response = storageClient.batchDeleteRecords(EMPTY,
        new String[] {RECORD_ID_1, RECORD_ID_2});
    assertEquals(HttpStatus.SC_NO_CONTENT, response.statusCode());

    assertRecordNotFound(RECORD_ID_1);
    assertRecordNotFound(RECORD_ID_2);
  }

  @Test
  public void should_deleteRecordsLogically_withPartialSuccess_whenOneRecordNotFound() {
    HttpResponse<BatchDeleteRecordError[]> deleteResponse = storageClient.batchDeleteRecords(
        EMPTY, new String[] {RECORD_ID_1, RECORD_ID_2, NOT_EXISTED_RECORD_ID});
    assertEquals(SC_MULTI_STATUS, deleteResponse.statusCode());
    BatchDeleteRecordError[] jsonBody = deleteResponse.body();

    assertEquals(1, jsonBody.length);
    assertEquals(NOT_EXISTED_RECORD_ID, jsonBody[0].notDeletedRecordId());
    assertEquals("Record with id '" + NOT_EXISTED_RECORD_ID + "' not found", jsonBody[0].message());

    assertRecordNotFound(RECORD_ID_1);
    assertRecordNotFound(RECORD_ID_2);
  }

  private void assertRecordNotFound(String recordId) {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.getRecord(recordId));
    assertEquals(SC_NOT_FOUND, ex.getStatusCode());
  }

  private StorageRecord[] createRecords(String id, String dataValue, List<String> legalTags,
      List<String> ordc) {
    RecordAcl acl = new RecordAcl(new String[] {getAcl()}, new String[] {getAcl()});
    RecordLegal legal = new RecordLegal(
        legalTags.toArray(String[]::new), ordc.toArray(String[]::new));
    StorageRecord record = new StorageRecord(id, null, KIND, acl, Map.of("name", dataValue), legal, null, null,
        null, null, null, null, null);
    return new StorageRecord[] {record};
  }
}
