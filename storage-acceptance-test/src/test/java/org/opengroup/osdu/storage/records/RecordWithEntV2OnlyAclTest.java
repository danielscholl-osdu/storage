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

import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.entitlements.CreateGroupRequest;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opengroup.osdu.storage.util.TestUtils.STORAGE_TEST_GROUP_ENT_V_2;
import static org.opengroup.osdu.storage.util.TestUtils.STORAGE_TEST_GROUP_ENT_V_2_DESCRIPTION;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TestUtils;

public final class RecordWithEntV2OnlyAclTest extends BaseRecordsAcceptanceTest {

  private String LEGAL_TAG;
  private String KIND;
  private String RECORD_ID;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    LEGAL_TAG = getTenantId() + "-storage-" + now;
    KIND = getTenantId() + ":test:inttest:1.1." + now;
    RECORD_ID = getTenantId() + ":inttest:" + now;

    createLegalTag(LEGAL_TAG);
    try {
      CreateGroupRequest createGroupRequest = new CreateGroupRequest(
          STORAGE_TEST_GROUP_ENT_V_2,
          STORAGE_TEST_GROUP_ENT_V_2_DESCRIPTION
      );
      var createGroupResponse = entitlementsClient.createGroup(createGroupRequest);
      assertEquals(HttpStatus.SC_CREATED, createGroupResponse.statusCode());
    } catch (ClientException ex) {
      assertEquals(HttpStatus.SC_CONFLICT, ex.getStatusCode());
      assertEquals("This group already exists", ex.getMessage());
    }
  }

  @Test
  public void should_allow_recordWithAclThatExistsOnlyInEntV2() {
    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords(RecordUtil.replaceAcl(
        RecordUtil.createRecordsWithEntV2OnlyAcl(RECORD_ID, KIND, LEGAL_TAG, RECORD_ID),
        TestUtils.getEntV2OnlyAcl(), getEntV2OnlyAcl()));
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());
  }
}
