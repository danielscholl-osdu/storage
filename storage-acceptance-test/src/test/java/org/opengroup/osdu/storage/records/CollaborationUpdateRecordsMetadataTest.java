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
import org.opengroup.osdu.core.test.client.model.storage.UpdateRecordsMetadataResponse;

import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.opengroup.osdu.core.test.client.model.storage.UpdateRecordsMetadataRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class CollaborationUpdateRecordsMetadataTest extends BaseRecordsAcceptanceTest {

  private static final String APPLICATION_NAME =
      "storage service integration test for update records metadata";

  private boolean isCollaborationEnabled;
  private String recordPatchId;
  private String collaboration1Id;
  private Long recordPatchV1;
  private Long recordPatchV2;

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
    collaboration1Id = CollaborationRecordsPurgeTest.COLLABORATION1_ID;
    String legalTagName = createLegalTagName("");
    String kind = getTenantId() + ":ds:patchtest:1" + now;
    recordPatchId = getTenantId() + ":patchtest:1" + now;
    createLegalTag(legalTagName);

    recordPatchV1 = createRecordInCollaborationAndReturnVersion(recordPatchId, kind, legalTagName,
        null, APPLICATION_NAME);
    recordPatchV2 = createRecordInCollaborationAndReturnVersion(recordPatchId, kind, legalTagName,
        collaboration1Id, APPLICATION_NAME);

    UpdateRecordsMetadataRequest updateBody = RecordUtil.buildUpdateTagsMetadata(recordPatchId, "add",
        UpdateRecordsMetadataTest.TAG_KEY + ":" + UpdateRecordsMetadataTest.TAG_VALUE1);
    HttpResponse<UpdateRecordsMetadataResponse> patchResponse = storageClient.patchRecords(updateBody,
        collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertEquals(SC_OK, patchResponse.statusCode());
  }

  @Test
  public void shouldMaintainAndUpdateRecordInRespectiveCollaborationContext() {
    if (!isCollaborationEnabled) {
      return;
    }
    var getResponse = storageClient.getRecord(recordPatchId, collaborationHeaders(null, APPLICATION_NAME));
    StorageRecord record = assertRecordVersion(getResponse, recordPatchV1);
    assertNull(record.tags());

    getResponse = storageClient.getRecord(recordPatchId, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    record = assertRecordVersion(getResponse, recordPatchV2);
    assertTrue(record.tags().containsKey(UpdateRecordsMetadataTest.TAG_KEY));
    assertEquals(UpdateRecordsMetadataTest.TAG_VALUE1,
        record.tags().get(UpdateRecordsMetadataTest.TAG_KEY));
  }

  @AfterEach
  void cleanupCollaborationRecords() {
    if (!isCollaborationEnabled) {
      return;
    }
    storageClient.deleteRecord(recordPatchId, collaborationHeaders(null, APPLICATION_NAME));
    storageClient.deleteRecord(recordPatchId, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
  }
}
