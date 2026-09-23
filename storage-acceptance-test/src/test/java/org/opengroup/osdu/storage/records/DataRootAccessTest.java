/*
 *  Copyright 2020-2023 Google LLC
 *  Copyright 2020-2023 EPAM Systems, Inc
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
import org.opengroup.osdu.core.test.client.model.entitlements.CreateGroupRequest;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;

import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.Records;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.opengroup.osdu.core.test.client.model.storage.QueryRecordsRequest;
import org.opengroup.osdu.core.test.auth.UserType;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.StorageClient;
import org.opengroup.osdu.storage.util.RecordUtil;

import java.util.List;

public final class DataRootAccessTest extends BaseRecordsAcceptanceTest {

  private final StorageClient storageClientRootUser;

  private String LEGAL_TAG;
  private String KIND;
  private String RECORD_ID;
  private String DATA_GROUP_ID;
  private String GROUP_EMAIL;

  DataRootAccessTest() {
    super(List.of(UserType.PRIVILEGED_USER, UserType.ROOT_USER));
    this.storageClientRootUser = new StorageClient(this.stringHttpClient, UserType.ROOT_USER);
  }

  @BeforeEach
  @Override
  protected void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    LEGAL_TAG = getTenantId() + "-storage-" + now;
    KIND = getTenantId() + ":data-root-test:no:1.1." + now;
    RECORD_ID = getTenantId() + ":data-root-test:1.1." + now;
    DATA_GROUP_ID = "data.test-users-data-root." + now;
    createLegalTag(LEGAL_TAG);

    GROUP_EMAIL = createDataGroup();
  }

  @AfterEach
  @Override
  protected void teardown() {
    storageClientRootUser.teardown();
    super.teardown();
  }

  @Test
  public void shouldHaveAccessToNewlyCreatedDataGroupWhenBelongsToUsersDataRoot() {
    StorageRecord[] createRecordBody = RecordUtil.createRecordsWithCustomAcl(RECORD_ID, KIND, LEGAL_TAG,
        GROUP_EMAIL);
    HttpResponse<CreateRecordsResponse> response = storageClientRootUser.putRecords(createRecordBody);
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());

    var queryResponse = storageClientRootUser.queryRecordsPost(QueryRecordsRequest.of(RECORD_ID));

    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());

    Records responseObject = queryResponse.body();
    assertEquals(1, responseObject.records().length);
    assertEquals(RECORD_ID, responseObject.records()[0].id());

    HttpResponse<Void> deleteResponse = storageClientRootUser.deleteRecord(RECORD_ID);
    assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
  }

  private String createDataGroup() {
    CreateGroupRequest createGroupRequest = new CreateGroupRequest(
        DATA_GROUP_ID,
        "Used in ACL, to test that users.data.root have access to any data group."
    );
    var entitlementsGroup = entitlementsClient.createGroup(createGroupRequest);
    assertEquals(HttpStatus.SC_CREATED, entitlementsGroup.statusCode());
    return entitlementsGroup.body().email();
  }
}
