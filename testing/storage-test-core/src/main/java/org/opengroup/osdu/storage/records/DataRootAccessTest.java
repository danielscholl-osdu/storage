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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.*;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class DataRootAccessTest extends TestBase {

  protected static long NOW = System.currentTimeMillis();
  protected static String LEGAL_TAG = LegalTagUtils.createRandomName();
  protected static String KIND = TenantUtils.getTenantName() + ":data-root-test:no:1.1." + NOW;
  protected static String RECORD_ID = TenantUtils.getTenantName() + ":data-root-test:1.1." + NOW;
  protected static String DATA_GROUP_ID = "data.test-users-data-root." + NOW;
  protected static String GROUP_DESCRIPTION = "Used in ACL, to test that users.data.root have access to any data group.";
  protected static String GROUP_EMAIL;

  public static void classSetup(String token) throws Exception {
    LegalTagUtils.create(LEGAL_TAG, token);
    Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), token);
    GROUP_EMAIL = createDataGroup(headers);
  }

  public static void classTearDown(String token) throws Exception {
    Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), token);
    deleteDataGroup(headers, GROUP_EMAIL);
    LegalTagUtils.delete(LEGAL_TAG, token);
  }

  @Test
  public void shouldHaveAccessToNewlyCreatedDataGroupWhenBelongsToUsersDataRoot() throws Exception {
    JsonArray records = new JsonArray();
    records.add(RECORD_ID);

    JsonObject body = new JsonObject();
    body.add("records", records);

    String dataRootUserToken = testUtils.getDataRootUserToken();

    Map<String, String> headersWithUsersDataRootAccess = HeaderUtils.getHeaders(
        TenantUtils.getTenantName(),
        dataRootUserToken);

    // Data root user doesn't belong to a newly created group,
    // but should be able to create the record with that group
    String createRecordBody = RecordUtil.createJsonRecordWithCustomAcl(RECORD_ID, KIND, LEGAL_TAG,
        GROUP_EMAIL);
    CloseableHttpResponse response = TestUtils.send(
        "records",
        "PUT",
        headersWithUsersDataRootAccess,
        createRecordBody,
        ""
    );
    assertEquals(HttpStatus.SC_CREATED, response.getCode());

    // And read it
    CloseableHttpResponse queryResponse = TestUtils.send(
        "query/records",
        "POST",
        headersWithUsersDataRootAccess,
        body.toString(),
        ""
    );

    DummyRecordsHelper.RecordsMock responseObject = new DummyRecordsHelper().getRecordsMockFromResponse(
        queryResponse);

    assertEquals(HttpStatus.SC_OK, queryResponse.getCode());
    assertEquals(1, responseObject.records.length);
    assertEquals(RECORD_ID, Stream.of(responseObject.records).findFirst().get().id);

    // And then delete it
    CloseableHttpResponse deleteResponse = TestUtils.send(
        "records/" + RECORD_ID, "DELETE",
        HeaderUtils.getHeaders(TenantUtils.getTenantName(), dataRootUserToken),
        "",
        ""
    );
    assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.getCode());
  }

  protected static String createDataGroup(Map<String, String> headersWithValidAccessToken)
      throws Exception {
    CloseableHttpResponse entitlementsGroup = EntitlementsUtil.createEntitlementsGroup(
        headersWithValidAccessToken,
        DATA_GROUP_ID,
        GROUP_DESCRIPTION
    );
    assertTrue(entitlementsGroup.getEntity().getContentType().contains("application/json"));
    String json = EntityUtils.toString(entitlementsGroup.getEntity());
    Gson gson = new Gson();
    JsonObject groupEntity = gson.fromJson(json, JsonObject.class);
    return groupEntity.get("email").getAsString();
  }

  protected static void deleteDataGroup(Map<String, String> headersWithValidAccessToken,
      String groupEmail) throws Exception {
    EntitlementsUtil.deleteEntitlementsGroup(headersWithValidAccessToken, groupEmail);
  }
}
