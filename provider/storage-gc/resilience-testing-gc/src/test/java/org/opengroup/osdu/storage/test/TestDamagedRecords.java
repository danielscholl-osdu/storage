/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
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

package org.opengroup.osdu.storage.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengroup.osdu.storage.config.EnvironmentConfiguration.ACL_OWNERS;
import static org.opengroup.osdu.storage.config.EnvironmentConfiguration.ACL_VIEWERS;
import static org.opengroup.osdu.storage.config.EnvironmentConfiguration.STORAGE_API;
import static org.opengroup.osdu.storage.config.EnvironmentConfiguration.STORAGE_API_BATCH;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.config.EnvironmentConfiguration;
import org.opengroup.osdu.storage.util.AuthUtil;
import org.opengroup.osdu.storage.util.DatastoreUtil;
import org.opengroup.osdu.storage.util.FileLoadUtil;
import org.opengroup.osdu.storage.util.HttpClient;

@Slf4j
public class TestDamagedRecords {

  public static final String RECORD_ID = "osdu:test:test-error-handling:";
  public static final String DATA_PARTITION_ID_HEADER = "data-partition-id";
  public static final String AUTHORIZATION_HEADER = "authorization";
  public static final String RECORDS_PROPERTY = "records";
  public static final ArrayList<String> RECORD_IDS = new ArrayList<>();

  private final EnvironmentConfiguration configuration = new EnvironmentConfiguration();
  private final Gson gson = new Gson();
  private final DatastoreUtil datastoreUtil = new DatastoreUtil(configuration);

  private static JsonArray preparePutRecordsRequestBody(String record, String aclViewers, String aclOwners,
      String legaTag) {
    JsonArray jsonArray = new JsonArray();

    for (int i = 0; i < 5; i++) {
      String recordId = RECORD_ID + i;
      RECORD_IDS.add(recordId);
      Map<String, String> substitutorConfig = Map.of(
          "record_id", recordId,
          "acl_viewers", aclViewers,
          "acl_owners", aclOwners,
          "legal_tag", legaTag
      );
      StringSubstitutor stringSubstitutor = new StringSubstitutor(substitutorConfig);
      String updatedRecord = stringSubstitutor.replace(record);
      jsonArray.add(JsonParser.parseString(updatedRecord));
    }
    return jsonArray;
  }

  @Test
  public void testRecordsWithCorruptedVersionInfo() throws IOException, ParseException {
    String record = FileLoadUtil.loadRecordFromFile("records/test-version-absent-record.json");
    String aclViewers = ACL_VIEWERS.formatted(configuration.getTenant(), configuration.getEntitlementsDomain());
    String aclOwners = ACL_OWNERS.formatted(configuration.getTenant(), configuration.getEntitlementsDomain());
    String legaTag = configuration.getLegaTag();

    JsonArray jsonArray = preparePutRecordsRequestBody(record, aclViewers, aclOwners, legaTag);

    // create 5 records
    HttpResponse httpResponse = createStorageRecords(jsonArray);
    assertEquals(HttpStatus.SC_CREATED, httpResponse.getCode());

    String firstDamagedRecord = RECORD_ID + 3;
    String secondDamagedRecord = RECORD_ID + 4;

    // damage 2 of them
    datastoreUtil.deleteVersionsFromRecordMeta(List.of(firstDamagedRecord, secondDamagedRecord));

    // expect that in response we will see 3 valid records and 2 not found
    CloseableHttpResponse batchResponse = batchFetchStorageRecords(RECORD_IDS);
    assertEquals(HttpStatus.SC_OK, batchResponse.getCode());

    JsonObject jsonResponseObject = gson.fromJson(EntityUtils.toString(batchResponse.getEntity()), JsonObject.class);
    JsonElement notFoundRecords = jsonResponseObject.get("notFound");
    JsonArray validRecords = jsonResponseObject.get(RECORDS_PROPERTY).getAsJsonArray();
    assertEquals(3, validRecords.size());
    assertTrue(notFoundRecords.getAsJsonArray().contains(new JsonPrimitive(firstDamagedRecord)));
    assertTrue(notFoundRecords.getAsJsonArray().contains(new JsonPrimitive(secondDamagedRecord)));
  }

  private CloseableHttpResponse createStorageRecords(JsonArray jsonArray) {
    StringEntity body = new StringEntity(jsonArray.toString(), ContentType.APPLICATION_JSON);
    HttpPut updateOrCreateRecords = new HttpPut(configuration.getOsduHost() + STORAGE_API);
    updateOrCreateRecords.setEntity(body);

    updateOrCreateRecords.addHeader(DATA_PARTITION_ID_HEADER, configuration.getTenant());
    updateOrCreateRecords.addHeader(AUTHORIZATION_HEADER, AuthUtil.getToken());

    return HttpClient.sendRequest(updateOrCreateRecords);
  }

  private CloseableHttpResponse batchFetchStorageRecords(ArrayList<String> recordIds) {
    HttpPost getRecordsBatch = new HttpPost(configuration.getOsduHost() + STORAGE_API_BATCH);
    getRecordsBatch.addHeader(DATA_PARTITION_ID_HEADER, configuration.getTenant());
    getRecordsBatch.addHeader(AUTHORIZATION_HEADER, AuthUtil.getToken());
    JsonObject records = new JsonObject();
    records.add(RECORDS_PROPERTY, gson.toJsonTree(recordIds));
    StringEntity getRecordsBatchBody = new StringEntity(records.toString(), ContentType.APPLICATION_JSON);
    getRecordsBatch.setEntity(getRecordsBatchBody);
    return HttpClient.sendRequest(getRecordsBatch);
  }
}
