/*
 * Copyright 2025 bp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.records;

import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.RecordsListResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GetAllRecordsTest extends BaseRecordsAcceptanceTest {

  private String recordId1;
  private String recordId2;
  private String recordId3;
  private String kind;
  private List<String> generatedRecordsList;
  private List<String> softDeletedRecordsList;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    String timestamp = String.valueOf(System.currentTimeMillis());
    recordId1 = getTenantId() + ":readAllRecords:1" + timestamp;
    recordId2 = getTenantId() + ":readAllRecords:2" + timestamp;
    recordId3 = getTenantId() + ":readAllRecords:3" + timestamp;
    kind = getTenantId() + ":ds:readAllRecords:" + timestamp;
    generatedRecordsList = new ArrayList<>();
    softDeletedRecordsList = new ArrayList<>();

    String legalTagName = createLegalTagName("");
    createLegalTag(legalTagName);
    createRecordAndReturnVersion(recordId1, kind, legalTagName);
    createRecordAndReturnVersion(recordId2, kind, legalTagName);
    createRecordAndReturnVersion(recordId3, kind, legalTagName);

    generatedRecordsList.add(recordId1);
    generatedRecordsList.add(recordId3);
    generatedRecordsList.add(recordId2);
  }

  private void softDeleteRecord(String recordId) {
    HttpResponse<Void> response = storageClient.softDeleteRecord(recordId);
    assertEquals(HttpStatus.SC_NO_CONTENT, response.statusCode());
  }

  @Test
  public void should_fetchAllRecords() {
    var response = storageClient.getRecords("?kind=" + kind);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    RecordsListResponse responseJson = response.body();
    verifyGetAllActiveRecordsSuccessResponse(responseJson);
  }

  @Test
  public void should_fetchOnlyActiveRecordsWhenNoDeleteFilterSpecified() {
    softDeleteRecord(recordId2);
    softDeletedRecordsList.add(recordId2);

    var response = storageClient.getRecords("?kind=" + kind);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    RecordsListResponse responseJson = response.body();
    verifyGetAllActiveRecordsSuccessResponse(responseJson);
  }

  @Test
  public void should_fetchSoftDeletedRecordsWhenFilterSpecified() {
    softDeleteRecord(recordId2);
    softDeletedRecordsList.add(recordId2);

    var response = storageClient.getRecords("?deleted=true&kind=" + kind);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    RecordsListResponse responseJson = response.body();

    assertEquals(softDeletedRecordsList.size(), responseJson.results().length,
        "Results array should contain exactly one object");

    StorageRecord inactiveRecord = responseJson.results()[0];
    assertEquals(recordId2, inactiveRecord.id());
  }

  @Test
  public void should_returnUnauthorized_whenTokenNotProvided() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.listRecords("", Map.of("Authorization", "")));
    assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getStatusCode());
  }

  @Test
  public void should_returnBadRequest_whenDataPartitionHeaderMissing() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.listRecords("?kind=" + kind, Map.of("data-partition-id", "")));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_returnBadRequest_when_limitExceedsMaximum() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.getRecords("?limit=101&kind=" + kind));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_returnBadRequest_when_limitIsZero() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.getRecords("?limit=0&kind=" + kind));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_returnBadRequest_when_invalidKindParamPassed() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.getRecords("?kind=invalid-kind-format"));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  private void verifyGetAllActiveRecordsSuccessResponse(RecordsListResponse responseJson) {
    assertNotNull(responseJson.results(), "Response should contain 'results' field");
    assertEquals(generatedRecordsList.size() - softDeletedRecordsList.size(),
        responseJson.results().length,
        "Results array should contain all active records.");

    StorageRecord record = responseJson.results()[0];
    String recordId = record.id();
    if (!(recordId1.equalsIgnoreCase(recordId) || recordId2.equalsIgnoreCase(recordId)
        || recordId3.equalsIgnoreCase(recordId))) {
      fail("Results array should contain one of the generated Records");
    }
    assertEquals(kind, record.kind());
  }
}
