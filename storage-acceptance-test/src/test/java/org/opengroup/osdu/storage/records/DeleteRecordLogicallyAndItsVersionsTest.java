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

import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.model.storage.RecordVersions;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class DeleteRecordLogicallyAndItsVersionsTest extends BaseRecordsAcceptanceTest {

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
    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords(
        withTestAcl(RecordUtil.createRecordsWithData(RECORD_ID, KIND, LEGAL_TAG, "v1")));
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());
  }

  @Test
  public void should_deleteRecordAndAllVersionsLogically_when_userIsAuthorized() {
    HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(
        withTestAcl(RecordUtil.createRecordsWithData(RECORD_ID, KIND, LEGAL_TAG, "v2")));
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

    var versionsResponse = storageClient.getRecordVersions(RECORD_ID);
    assertEquals(HttpStatus.SC_OK, versionsResponse.statusCode());

    RecordVersions content = versionsResponse.body();
    String versionOne = content.versions()[0].toString();
    String versionTwo = content.versions()[1].toString();

    HttpResponse<Void> deleteResponse = storageClient.softDeleteRecords(RECORD_ID + ":delete",
        Map.of("anything", "anything"));
    assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());

    ClientException notFoundVersionOne = assertThrows(ClientException.class,
        () -> storageClient.getRecordVersion(RECORD_ID, versionOne));
    assertEquals(HttpStatus.SC_NOT_FOUND, notFoundVersionOne.getStatusCode());

    ClientException notFoundVersionTwo = assertThrows(ClientException.class,
        () -> storageClient.getRecordVersion(RECORD_ID, versionTwo));
    assertEquals(HttpStatus.SC_NOT_FOUND, notFoundVersionTwo.getStatusCode());
  }
}
