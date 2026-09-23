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

package org.opengroup.osdu.storage.pubsubendpoint;

import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class PubsubEndpointTest extends BaseStorageAcceptanceTest {

  private static final long NOW = System.currentTimeMillis();
  private static final long FIVE_SECOND_LATER = NOW + 5000L;

  private static String LEGAL_TAG_1;
  private static String LEGAL_TAG_2;
  private static String RECORD_ID;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    LEGAL_TAG_1 = getTenantId() + "-storage-" + NOW;
    LEGAL_TAG_2 = LEGAL_TAG_1 + "random2";
    String KIND = getTenantId() + ":test:endtoend:1.1." + NOW;
    RECORD_ID = getTenantId() + ":endtoend:1.1." + NOW;
    String RECORD_ID_2 = getTenantId() + ":endtoend:1.1." + FIVE_SECOND_LATER;

    createLegalTag(LEGAL_TAG_1);
    StorageRecord[] record1 = RecordUtil.createDefaultRecords(RECORD_ID, KIND, LEGAL_TAG_1);
    HttpResponse<CreateRecordsResponse> responseValid = storageClient.putRecords(record1);
    assertEquals(HttpStatus.SC_CREATED, responseValid.statusCode());

    createLegalTag(LEGAL_TAG_2);
    StorageRecord[] record2 = RecordUtil.createDefaultRecords(RECORD_ID_2, KIND, LEGAL_TAG_2);
    HttpResponse<CreateRecordsResponse> responseValid2 = storageClient.putRecords(record2);
    assertEquals(HttpStatus.SC_CREATED, responseValid2.statusCode());
  }

  @Test
  public void should_deleteIncompliantLegalTagAndInvalidateRecordsAndNotIngestAgain_whenIncompliantMessageSentToEndpoint()
      throws Exception {
    legalTagClient.delete(LEGAL_TAG_1);
    // wait until cache of opa will be rebuild
    Thread.sleep(100000);

    assertThrows(ClientException.class, () -> storageClient.getRecord(RECORD_ID));

    long now = System.currentTimeMillis();
    long later = now + 2000L;
    String recordIdTemp1 = getTenantId() + ":endtoend:1.1." + now;
    String kindTemp = getTenantId() + ":test:endtoend:1.1." + now;
    StorageRecord[] recordTemp1 = RecordUtil.createDefaultRecords(recordIdTemp1, kindTemp, LEGAL_TAG_1);
    String recordIdTemp2 = getTenantId() + ":endtoend:1.1." + later;
    StorageRecord[] recordTemp2 = RecordUtil.createDefaultRecords(recordIdTemp2, kindTemp, LEGAL_TAG_2);

    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.putRecords(recordTemp1));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("Invalid legal tags", ex.getError().getReason());

    HttpResponse<CreateRecordsResponse> responseValid3 =
        storageClient.putRecords(recordTemp2);
    assertEquals(HttpStatus.SC_CREATED, responseValid3.statusCode());

    storageClient.deleteRecord(recordIdTemp2);
  }

}
