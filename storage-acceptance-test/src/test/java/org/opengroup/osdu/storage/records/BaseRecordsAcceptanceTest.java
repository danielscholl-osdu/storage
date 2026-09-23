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

import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.hc.core5.http.HttpStatus;
import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TestUtils;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for storage records acceptance tests.
 */
public abstract class BaseRecordsAcceptanceTest extends BaseStorageAcceptanceTest {

  // Dynamically created integration test group
  protected String integrationTestGroupEmail;

  protected BaseRecordsAcceptanceTest() {
  }

  protected BaseRecordsAcceptanceTest(List<UserType> userTypes) {
    super(userTypes);
  }

  @BeforeAll
  void createIntegrationTestGroupOnce() throws InterruptedException {
    String randomGroupName = "data.inttest-" + UUID.randomUUID();
    var createGroupResponse = entitlementsClient.createGroup(
        randomGroupName, "Integration test group for storage acceptance tests");
    assertEquals(HttpStatus.SC_CREATED, createGroupResponse.statusCode());
    integrationTestGroupEmail = createGroupResponse.body().email();
    Thread.sleep(3000);
  }

  protected static final String COLLABORATION_HEADER = "x-collaboration";

  protected String getEntV2OnlyAcl() {
    return String.format("%s@%s", TestUtils.STORAGE_TEST_GROUP_ENT_V_2, getAclSuffix());
  }

  protected String getIntegrationTesterAcl() {
    return integrationTestGroupEmail;
  }

  protected StorageRecord[] withTestAcl(StorageRecord[] records) {
    return RecordUtil.replaceAcl(records, TestUtils.getAcl(), getAcl());
  }

  protected Map<String, String> collaborationHeaders(String collaborationId,
      String applicationName) {
    Map<String, String> headers = new HashMap<>();
    if (!Strings.isNullOrEmpty(collaborationId)) {
      headers.put(COLLABORATION_HEADER,
          "id=" + collaborationId + ",application=" + applicationName);
    }
    return headers;
  }

  protected Map<String, String> collaborationHeadersWithoutId(String collaborationId,
      String applicationName) {
    Map<String, String> headers = new HashMap<>();
    if (Strings.isNullOrEmpty(collaborationId)) {
      headers.put(COLLABORATION_HEADER, "application=" + applicationName);
    } else {
      headers.put(COLLABORATION_HEADER,
          "id=" + collaborationId + ",application=" + applicationName);
    }
    return headers;
  }

  protected Long createRecordAndReturnVersion(String recordId, String kind, String legalTag) {
    var createResponse = storageClient.putRecords(withTestAcl(RecordUtil.createDefaultRecords(recordId, kind, legalTag)));
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    CreateRecordsResponse result = createResponse.body();
    return Long.parseLong(result.recordIdVersions()[0].split(":")[3]);
  }

  protected Long createRecordInCollaborationAndReturnVersion(String recordId, String kind,
      String legalTag, String collaborationId, String applicationName) {
    var createResponse = storageClient.putRecords(withTestAcl(RecordUtil.createDefaultRecords(recordId, kind, legalTag)),
        collaborationHeaders(collaborationId, applicationName));
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    CreateRecordsResponse result = createResponse.body();
    return Long.parseLong(result.recordIdVersions()[0].split(":")[3]);
  }

  protected StorageRecord assertRecordVersion(
      HttpResponse<StorageRecord> response, Long expectedVersion) {
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    StorageRecord result = response.body();
    assertEquals(expectedVersion.longValue(), Long.parseLong(result.version()));
    return result;
  }
}
