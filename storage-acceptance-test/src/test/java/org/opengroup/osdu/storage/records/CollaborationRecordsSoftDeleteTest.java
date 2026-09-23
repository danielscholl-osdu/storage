/*
 *  Copyright 2020-2024 Google LLC
 *  Copyright 2020-2024 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.records;

import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class CollaborationRecordsSoftDeleteTest extends BaseRecordsAcceptanceTest {

  private static final String APPLICATION_NAME =
      "storage service integration test for soft delete";

  private boolean isCollaborationEnabled;
  private String collaboration1Id;
  private String collaboration2Id;
  private String recordId1;
  private String recordId2;
  private String recordId3;
  private Long record1V3;
  private Long record1V4;
  private Long record2V2;
  private Long record3V2;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    if (!configUtils.getIsCollaborationEnabled()) {
      isCollaborationEnabled = false;
      return;
    }
    isCollaborationEnabled = true;
    long now = System.currentTimeMillis();
    collaboration1Id = UUID.randomUUID().toString();
    collaboration2Id = UUID.randomUUID().toString();
    recordId1 = getTenantId() + ":inttest:1" + now;
    recordId2 = getTenantId() + ":inttest:2" + now;
    recordId3 = getTenantId() + ":inttest:3" + now;
    String kind = getTenantId() + ":ds:inttest:" + now;
    String legalTagName = createLegalTagName("");
    createLegalTag(legalTagName);

    createRecordInCollaborationAndReturnVersion(recordId1, kind, legalTagName, collaboration1Id,
        APPLICATION_NAME);
    createRecordInCollaborationAndReturnVersion(recordId1, kind, legalTagName, collaboration1Id,
        APPLICATION_NAME);
    record1V3 = createRecordInCollaborationAndReturnVersion(recordId1, kind, legalTagName, null,
        APPLICATION_NAME);
    record1V4 = createRecordInCollaborationAndReturnVersion(recordId1, kind, legalTagName,
        collaboration2Id, APPLICATION_NAME);

    createRecordInCollaborationAndReturnVersion(recordId2, kind, legalTagName, collaboration1Id,
        APPLICATION_NAME);
    record2V2 = createRecordInCollaborationAndReturnVersion(recordId2, kind, legalTagName,
        collaboration2Id, APPLICATION_NAME);

    createRecordInCollaborationAndReturnVersion(recordId3, kind, legalTagName, collaboration1Id,
        APPLICATION_NAME);
    record3V2 = createRecordInCollaborationAndReturnVersion(recordId3, kind, legalTagName,
        collaboration2Id, APPLICATION_NAME);
  }

  @AfterEach
  void cleanupCollaborationRecords() {
    if (!isCollaborationEnabled) {
      return;
    }
    storageClient.deleteRecord(recordId1, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    storageClient.deleteRecord(recordId1, collaborationHeaders(null, APPLICATION_NAME));
    storageClient.deleteRecord(recordId1, collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    storageClient.deleteRecord(recordId2, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    storageClient.deleteRecord(recordId2, collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    storageClient.deleteRecord(recordId3, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    storageClient.deleteRecord(recordId3, collaborationHeaders(collaboration2Id, APPLICATION_NAME));
  }

  @Test
  public void should_softDeleteSingleRecordWithinCollaborationContext_when_validRecordIdsAndCollaborationIdAreProvided() {
    if (!isCollaborationEnabled) {
      return;
    }
    HttpResponse<Void> deleteResponse = storageClient.softDeleteRecord(recordId1,
        collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());

    assertRecordNotFound(recordId1, collaborationHeaders(collaboration1Id, APPLICATION_NAME));

    HttpResponse<StorageRecord> response = storageClient.getRecord(recordId1,
        collaborationHeaders(null, APPLICATION_NAME));
    assertRecordVersion(response, record1V3);

    response = storageClient.getRecord(recordId1,
        collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    assertRecordVersion(response, record1V4);
  }

  @Test
  public void should_bulkSoftDeleteWithinCollaborationContext_when_validRecordIdsAndCollaborationIdAreProvided() {
    if (!isCollaborationEnabled) {
      return;
    }
    var deleteResponse = storageClient.batchDeleteRecords("",
        new String[] {recordId2, recordId3},
        collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());

    assertRecordNotFound(recordId2, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertRecordNotFound(recordId3, collaborationHeaders(collaboration1Id, APPLICATION_NAME));

    HttpResponse<StorageRecord> response = storageClient.getRecord(recordId2,
        collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    assertRecordVersion(response, record2V2);

    response = storageClient.getRecord(recordId3,
        collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    assertRecordVersion(response, record3V2);
  }

  private void assertRecordNotFound(String recordId, Map<String, String> headers) {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.getRecord(recordId, headers));
    assertEquals(HttpStatus.SC_NOT_FOUND, ex.getStatusCode());
  }
}
