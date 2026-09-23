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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.RecordAcl;
import org.opengroup.osdu.core.test.client.model.storage.RecordLegal;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class LogicalRecordDeleteTests extends BaseRecordsAcceptanceTest {

  private String KIND;
  private String RECORD_ID;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    String LEGAL_TAG = getTenantId() + "-storage-" + now;
    KIND = getTenantId() + ":delete:inttest:1.0." + now;
    RECORD_ID = getTenantId() + ":inttest:" + now;

    createLegalTag(LEGAL_TAG);
    var createResponse = storageClient.putRecords(createRecords(RECORD_ID, "anything", Lists.newArrayList(
            LEGAL_TAG),
            Lists.newArrayList("BR", "IT")));
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    CreateRecordsResponse result = createResponse.body();
    assertEquals(1, result.recordCount());
    assertEquals(1, result.recordIds().length);
    assertEquals(1, result.recordIdVersions().length);
    assertEquals(RECORD_ID, result.recordIds()[0]);
  }

  @Test
  public void should_notRetrieveRecord_and_notDeleteRecordAgain_when_deletingItLogically() {
    HttpResponse<Void> deleteResponse = storageClient.softDeleteRecord(RECORD_ID,
        "{'anything':'teste'}", null);
    assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());

    ClientException notFoundOnGet = assertThrows(ClientException.class,
        () -> storageClient.getRecord(RECORD_ID));
    assertEquals(HttpStatus.SC_NOT_FOUND, notFoundOnGet.getStatusCode());

    ClientException notFoundOnDelete = assertThrows(ClientException.class,
        () -> storageClient.softDeleteRecord(RECORD_ID, "{'anything':'teste'}", null));
    assertEquals(HttpStatus.SC_NOT_FOUND, notFoundOnDelete.getStatusCode());
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
