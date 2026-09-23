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

import org.opengroup.osdu.core.test.client.model.storage.ConvertedRecords;
import org.opengroup.osdu.core.test.client.model.storage.QueryResult;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.RecordVersions;
import org.opengroup.osdu.core.test.client.model.storage.Records;

import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.opengroup.osdu.core.test.client.model.storage.QueryRecordsRequest;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;

public final class CollaborationRecordsRetrieveTest extends BaseRecordsAcceptanceTest {

  private static final String APPLICATION_NAME = "storage service integration test";

  private boolean isCollaborationEnabled;
  private String collaboration1Id;
  private String collaboration2Id;
  private String recordId1;
  private String recordId2;
  private String recordId3;
  private String kind1;
  private String kind2;
  private String kind3;
  private Long record1V1;
  private Long record1V2;
  private Long record1V3;
  private Long record1V4;
  private Long record2V1;
  private Long record2V2;
  private Long record3V1;
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
    kind1 = getTenantId() + ":ds:inttest:1" + now;
    kind2 = getTenantId() + ":ds:inttest:2" + now;
    kind3 = getTenantId() + ":ds:inttest:3" + now;
    String legalTagNameA = createLegalTagName("");
    createLegalTag(legalTagNameA);

    record1V1 = createRecordInCollaborationAndReturnVersion(recordId1, kind1, legalTagNameA, null,
        APPLICATION_NAME);
    record1V2 = createRecordInCollaborationAndReturnVersion(recordId1, kind1, legalTagNameA,
        collaboration1Id, APPLICATION_NAME);
    record1V3 = createRecordInCollaborationAndReturnVersion(recordId1, kind1, legalTagNameA,
        collaboration1Id, APPLICATION_NAME);
    record1V4 = createRecordInCollaborationAndReturnVersion(recordId1, kind1, legalTagNameA,
        collaboration2Id, APPLICATION_NAME);

    record2V1 = createRecordInCollaborationAndReturnVersion(recordId2, kind1, legalTagNameA, null,
        APPLICATION_NAME);
    record2V2 = createRecordInCollaborationAndReturnVersion(recordId2, kind1, legalTagNameA,
        collaboration2Id, APPLICATION_NAME);

    record3V1 = createRecordInCollaborationAndReturnVersion(recordId3, kind2, legalTagNameA,
        collaboration1Id, APPLICATION_NAME);
    record3V2 = createRecordInCollaborationAndReturnVersion(recordId3, kind2, legalTagNameA,
        collaboration2Id, APPLICATION_NAME);
  }

  @AfterEach
  void cleanupCollaborationRecords() {
    if (!isCollaborationEnabled) {
      return;
    }
    storageClient.deleteRecord(recordId1, collaborationHeaders(null, APPLICATION_NAME));
    storageClient.deleteRecord(recordId1, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    storageClient.deleteRecord(recordId1, collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    storageClient.deleteRecord(recordId2, collaborationHeaders(null, APPLICATION_NAME));
    storageClient.deleteRecord(recordId2, collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    storageClient.deleteRecord(recordId3, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    storageClient.deleteRecord(recordId3, collaborationHeaders(collaboration2Id, APPLICATION_NAME));
  }

  @Test
  public void should_getLatestVersion_when_validRecordIdAndCollaborationIdAreProvided() {
    if (!isCollaborationEnabled) {
      return;
    }
    HttpResponse<StorageRecord> response = storageClient.getRecord(recordId1, collaborationHeaders(null, APPLICATION_NAME));
    assertRecordVersion(response, record1V1);

    response = storageClient.getRecord(recordId1, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertRecordVersion(response, record1V3);

    response = storageClient.getRecord(recordId1, collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    assertRecordVersion(response, record1V4);

    ClientException notInCollaboration1 = assertThrows(ClientException.class,
        () -> storageClient.getRecord(recordId2,
            collaborationHeaders(collaboration1Id, APPLICATION_NAME)));
    assertEquals(HttpStatus.SC_NOT_FOUND, notInCollaboration1.getStatusCode());
  }

  @Test
  public void should_getCorrectRecordVersion_when_validRecordIdAndCollaborationIdAndRecordVersionAreProvided() {
    if (!isCollaborationEnabled) {
      return;
    }
    HttpResponse<StorageRecord> response = storageClient.getRecordVersion(recordId1,
        String.valueOf(record1V2),
        collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertRecordVersion(response, record1V2);

    ClientException wrongCollaborationVersion = assertThrows(ClientException.class,
        () -> storageClient.getRecordVersion(recordId1, String.valueOf(record1V2),
            collaborationHeaders(collaboration2Id, APPLICATION_NAME)));
    assertEquals(HttpStatus.SC_NOT_FOUND, wrongCollaborationVersion.getStatusCode());
  }

  @Test
  public void should_getAllRecordVersions_when_validRecordIdAndCollaborationIdAreProvided() {
    if (!isCollaborationEnabled) {
      return;
    }
    var versionsHttpResponse = storageClient.getRecordVersions(recordId1,
        collaborationHeaders(null, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, versionsHttpResponse.statusCode());
    RecordVersions versionsResponse = versionsHttpResponse.body();
    assertEquals(1, versionsResponse.versions().length);
    assertEquals(record1V1, versionsResponse.versions()[0]);

    versionsHttpResponse = storageClient.getRecordVersions(recordId1,
        collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, versionsHttpResponse.statusCode());
    versionsResponse = versionsHttpResponse.body();
    assertEquals(2, versionsResponse.versions().length);
    List<Long> versions = Arrays.asList(versionsResponse.versions());
    assertTrue(versions.contains(record1V2));
    assertTrue(versions.contains(record1V3));
  }

  @Test
  public void should_getRecordsOnlyInCollaborationContext_whenQueryByKind() {
    if (!isCollaborationEnabled) {
      return;
    }
    var response = storageClient.queryRecordsGet("?kind=" + kind1, collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    assertEquals(SC_OK, response.statusCode());
    QueryResult responseObject = response.body();
    assertEquals(2, responseObject.results().length);
    assertTrue(Arrays.asList(responseObject.results()).contains(recordId1));
    assertTrue(Arrays.asList(responseObject.results()).contains(recordId2));

    response = storageClient.queryRecordsGet("?kind=" + kind1, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertEquals(SC_OK, response.statusCode());
    responseObject = response.body();
    assertEquals(1, responseObject.results().length);
    assertTrue(Arrays.asList(responseObject.results()).contains(recordId1));

    response = storageClient.queryRecordsGet("?kind=" + kind3, collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertEquals(SC_OK, response.statusCode());
    responseObject = response.body();
    assertEquals(0, responseObject.results().length);
  }

  @Test
  public void should_getEmptyRecordsInNoCollaborationContext_whenQueryByKind() {
    if (!isCollaborationEnabled) {
      return;
    }
    var response = storageClient.queryRecordsGet("?kind=" + kind2, collaborationHeaders(null, null));
    assertEquals(SC_OK, response.statusCode());
    QueryResult responseObject = response.body();
    assertEquals(0, responseObject.results().length);
  }

  @Test
  public void should_fetchCorrectRecords_when_validRecordIdsAndCollaborationIdAreProvided() {
    if (!isCollaborationEnabled) {
      return;
    }
    var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId1, recordId2, recordId3),
        collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());
    ConvertedRecords responseObject = fetchResponse.body();
    assertEquals(2, responseObject.records().length);
    assertEquals(1, responseObject.notFound().length);
    assertEquals(0, responseObject.conversionStatuses().size());
    for (StorageRecord record : responseObject.records()) {
      if (record.id().equals(recordId1)) {
        assertEquals(record1V3, Long.valueOf(record.version()));
      } else if (record.id().equals(recordId2)) {
        fail("should not contain record 2: " + recordId2);
      } else if (record.id().equals(recordId3)) {
        assertEquals(record3V1, Long.valueOf(record.version()));
      } else {
        fail(String.format("should only contain record 1 %s, and record 3 %s", recordId1,
            recordId3));
      }
    }

    fetchResponse = storageClient.queryRecordsBatchPost(
        QueryRecordsRequest.of(recordId1, recordId2, recordId3),
        collaborationHeaders(null, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());
    responseObject = fetchResponse.body();
    assertEquals(2, responseObject.records().length);
    assertEquals(1, responseObject.notFound().length);
    assertEquals(0, responseObject.conversionStatuses().size());
    for (StorageRecord record : responseObject.records()) {
      if (record.id().equals(recordId1)) {
        assertEquals(record1V1, Long.valueOf(record.version()));
      } else if (record.id().equals(recordId2)) {
        assertEquals(record2V1, Long.valueOf(record.version()));
      } else if (record.id().equals(recordId3)) {
        fail("should not contain record 3: " + recordId3);
      } else {
        fail(String.format("should only contain record 1 %s, and record 2 %s", recordId1,
            recordId2));
      }
    }
  }

  @Test
  public void should_queryAllRecords_when_validRecordIdsAndCollaborationIdAreProvided() {
    if (!isCollaborationEnabled) {
      return;
    }
    var queryResponse = storageClient.queryRecordsPost(QueryRecordsRequest.of(recordId1, recordId2, recordId3),
        collaborationHeaders(collaboration2Id, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    Records responseObject = queryResponse.body();
    assertEquals(3, responseObject.records().length);
    assertEquals(0, responseObject.invalidRecords().length);
    assertEquals(0, responseObject.retryRecords().length);
    for (StorageRecord record : responseObject.records()) {
      if (record.id().equals(recordId1)) {
        assertEquals(record1V4, Long.valueOf(record.version()));
      } else if (record.id().equals(recordId2)) {
        assertEquals(record2V2, Long.valueOf(record.version()));
      } else if (record.id().equals(recordId3)) {
        assertEquals(record3V2, Long.valueOf(record.version()));
      } else {
        fail(String.format("should only contain record 1 %s, 2 %s and record 3 %s", recordId1,
            recordId2, recordId3));
      }
    }

    queryResponse = storageClient.queryRecordsPost(
        QueryRecordsRequest.of(recordId1, recordId2, recordId3),
        collaborationHeaders(collaboration1Id, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    responseObject = queryResponse.body();
    assertEquals(2, responseObject.records().length);
    assertEquals(1, responseObject.invalidRecords().length);
    assertEquals(0, responseObject.retryRecords().length);
    for (StorageRecord record : responseObject.records()) {
      if (record.id().equals(recordId1)) {
        assertEquals(record1V3, Long.valueOf(record.version()));
      } else if (record.id().equals(recordId2)) {
        fail("should not contain record 2: " + recordId2);
      } else if (record.id().equals(recordId3)) {
        assertEquals(record3V1, Long.valueOf(record.version()));
      } else {
        fail(String.format("should only contain record 1 %s, and record 3 %s", recordId1,
            recordId3));
      }
    }
  }
}
