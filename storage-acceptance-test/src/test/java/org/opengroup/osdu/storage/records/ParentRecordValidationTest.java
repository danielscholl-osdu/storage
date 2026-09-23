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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class ParentRecordValidationTest extends BaseRecordsAcceptanceTest {

  private String LEGAL_TAG;
  private String KIND;
  private String RECORD_ID;
  private String RECORD_ID_2;
  private String RECORD_ID_3;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    LEGAL_TAG = getTenantId() + "-storage-" + now;
    KIND = getTenantId() + ":bulkupdate:test:1.1." + now;
    RECORD_ID = getTenantId() + ":test:1.1." + now;
    RECORD_ID_2 = getTenantId() + ":test:1.2." + now;
    RECORD_ID_3 = getTenantId() + ":test:1.3." + now;
    createLegalTag(LEGAL_TAG);
  }

  @Test
  public void shouldReturn200_whenRecordContainsValidAncestry() {
    var createResponse = storageClient.putRecords(withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID, KIND, LEGAL_TAG)));
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    CreateRecordsResponse createResult = createResponse.body();
    String parentIdWithVersion = createResult.recordIdVersions()[0];

    HttpResponse<CreateRecordsResponse> response2 = storageClient.putRecords(withTestAcl(
        RecordUtil.createDefaultRecordsWithParentId(RECORD_ID_2, KIND, LEGAL_TAG,
            parentIdWithVersion)));
    assertEquals(HttpStatus.SC_CREATED, response2.statusCode());
  }

  @Test
  public void shouldReturn404_whenRecordAncestryNotExisted() {
    String parentIdWithVersion = "opendes:test:1.1.1000000000000:1000000000000000";
    String expectedErrorMessage = "The record 'RecordIdWithVersion(recordId=opendes:test:1.1.1000000000000, recordVersion=1000000000000000)' was not found";
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.putRecords(withTestAcl(
            RecordUtil.createDefaultRecordsWithParentId(RECORD_ID_3, KIND, LEGAL_TAG,
                parentIdWithVersion))));
    assertEquals(HttpStatus.SC_NOT_FOUND, ex.getStatusCode());
    assertEquals(expectedErrorMessage, ex.getError().getMessage());
  }
}
