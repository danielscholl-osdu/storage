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

import static org.apache.hc.core5.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.hc.core5.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class CollaborationRecordsPurgeTest extends BaseRecordsAcceptanceTest {

  static final String COLLABORATION1_ID = UUID.randomUUID().toString();

  private static final String APPLICATION_NAME = "storage service integration test";

  private boolean isCollaborationEnabled;
  private String recordPurgeId;
  private String collaboration2Id;
  private String kind1;
  private Long recordPurgeV3;
  private String legalTagNameA;

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
    recordPurgeId = getTenantId() + ":inttestpurge:1" + now;
    collaboration2Id = UUID.randomUUID().toString();
    kind1 = getTenantId() + ":ds:inttest:1" + now;
    legalTagNameA = createLegalTagName("");
    createLegalTag(legalTagNameA);

    createRecord(recordPurgeId, COLLABORATION1_ID);
    createRecord(recordPurgeId, COLLABORATION1_ID);
    recordPurgeV3 = createRecord(recordPurgeId, collaboration2Id);
  }

  @AfterEach
  void cleanupCollaborationRecords() {
    if (!isCollaborationEnabled) {
      return;
    }
    deleteRecordIgnoringNotFound(recordPurgeId,
        collaborationHeaders(COLLABORATION1_ID, APPLICATION_NAME));
    deleteRecordIgnoringNotFound(recordPurgeId,
        collaborationHeaders(collaboration2Id, APPLICATION_NAME));
  }

  @Test
  public void should_purgeAllRecordVersionsOnlyInCollaborationContext() {
    if (!isCollaborationEnabled) {
      return;
    }
    HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordPurgeId,
        collaborationHeaders(COLLABORATION1_ID, APPLICATION_NAME));
    assertEquals(SC_NO_CONTENT, deleteResponse.statusCode());

    assertRecordNotFound(recordPurgeId, collaborationHeaders(COLLABORATION1_ID, APPLICATION_NAME));

    HttpResponse<StorageRecord> getResponse = storageClient.getRecord(recordPurgeId,
        collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    assertEquals(SC_OK, getResponse.statusCode());
    assertEquals(recordPurgeV3.longValue(), Long.parseLong(getResponse.body().version()));
  }

  private void deleteRecordIgnoringNotFound(String recordId, Map<String, String> headers) {
    try {
      storageClient.deleteRecord(recordId, headers);
    } catch (ClientException ex) {
      if (ex.getStatusCode() != SC_NOT_FOUND) {
        throw ex;
      }
    }
  }

  private void assertRecordNotFound(String recordId, Map<String, String> headers) {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.getRecord(recordId, headers));
    assertEquals(SC_NOT_FOUND, ex.getStatusCode());
  }

  private Long createRecord(String recordId, String collaborationId) {
    return createRecordInCollaborationAndReturnVersion(recordId, kind1, legalTagNameA,
        collaborationId, APPLICATION_NAME);
  }
}
