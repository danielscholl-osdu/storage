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
import org.opengroup.osdu.core.test.client.model.storage.CopyRecordsRequest;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opengroup.osdu.storage.util.TestUtils.getCopyRecordRequest;

import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.RecordUtil;

@Slf4j
public final class CopyRecordReferencesTest extends BaseRecordsAcceptanceTest {

  private static final String APPLICATION_NAME = "storage service integration test";
  private static final String COLLABORATION_ID = "25c25830-8588-4b12-a0be-7263f2e43a09";
  private static final String COLLABORATION_ID_WIP_TO_WIP =
      "cfa0c1b0-421a-4f51-a2ac-84ff8a968736";

  private String recordIdSorToWip;
  private String recordIdWipToSor;
  private String recordIdWipToWip;
  private String recordIdExistInTarget;
  private String kind;
  private String legalTagNameA;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    assumeTrue(configUtils.getIsCollaborationEnabled());

    recordIdSorToWip = getTenantId() + ":getrecord:" + UUID.randomUUID();
    recordIdWipToSor = getTenantId() + ":getrecord:" + UUID.randomUUID();
    recordIdWipToWip = getTenantId() + ":getrecord:" + UUID.randomUUID();
    recordIdExistInTarget = getTenantId() + ":getrecord:" + UUID.randomUUID();
    kind = getTenantId() + ":ds:getrecord:1.0." + System.currentTimeMillis();
    legalTagNameA = createLegalTagName("");

    log.info("Test Ids: {}, {}, {}, {}", recordIdSorToWip, recordIdWipToSor, recordIdWipToWip,
        recordIdExistInTarget);
    createLegalTag(legalTagNameA);

    createRecord(recordIdSorToWip, null);
    createRecord(recordIdWipToSor, COLLABORATION_ID);
    createRecord(recordIdWipToWip, COLLABORATION_ID);
    createRecord(recordIdExistInTarget, COLLABORATION_ID);
  }

  @AfterEach
  void cleanupCollaborationRecords() throws Exception {
    storageClient.deleteRecord(recordIdSorToWip);
    storageClient.deleteRecord(recordIdWipToSor, collaborationHeaders(COLLABORATION_ID, APPLICATION_NAME));
    storageClient.deleteRecord(recordIdWipToWip, collaborationHeaders(COLLABORATION_ID, APPLICATION_NAME));
    Thread.sleep(100);
  }

  private void createRecord(String recordId, String collaborationId) {
    var createResponse = storageClient.putRecords(
        withTestAcl(RecordUtil.createDefaultRecords(recordId, kind, legalTagNameA)),
        collaborationHeaders(collaborationId, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    CreateRecordsResponse createResult = createResponse.body();
    assertEquals(recordId, createResult.recordIds()[0], "Creating record for copy test");
  }

  @Test
  public void should_copyRecord_from_sor_to_wip() {
    assertRecordNotFound(recordIdSorToWip,
        collaborationHeaders(COLLABORATION_ID, APPLICATION_NAME),
        "Check that record absent in target when copy from SOR to WIP");

    CopyRecordsRequest copyBody = getCopyRecordRequest(COLLABORATION_ID, recordIdSorToWip);
    HttpResponse<Void> responseCopy = storageClient.copyRecords(copyBody,
        collaborationHeadersWithoutId("", APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, responseCopy.statusCode(),
        "Check response after copy SOR to WIP");

    HttpResponse<StorageRecord> getResponse = storageClient.getRecord(recordIdSorToWip,
        collaborationHeaders(COLLABORATION_ID, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    StorageRecord copied = getResponse.body();
    assertEquals(recordIdSorToWip, copied.id(),
        "Get copied record from WIP when copy SOR to WIP");

    HttpResponse<Void> responseDelete = storageClient.deleteRecord(recordIdSorToWip,
        collaborationHeaders(COLLABORATION_ID, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_NO_CONTENT, responseDelete.statusCode(),
        "Check that record deleted when copy SOR to WIP");
  }

  @Test
  public void should_copyRecord_from_wip_to_sor() {
    ClientException notFoundInSor = assertThrows(ClientException.class,
        () -> storageClient.getRecord(recordIdWipToSor),
        "Check that record absent in target when copy WIP to SOR");
    assertEquals(HttpStatus.SC_NOT_FOUND, notFoundInSor.getStatusCode());

    CopyRecordsRequest copyBody = getCopyRecordRequest("", recordIdWipToSor);
    HttpResponse<Void> responseCopy = storageClient.copyRecords(copyBody,
        collaborationHeaders(COLLABORATION_ID, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, responseCopy.statusCode(), "Check response after copy WIP to SOR");

    var getResponse = storageClient.getRecord(recordIdWipToSor);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());

    StorageRecord copied = getResponse.body();
    assertEquals(recordIdWipToSor, copied.id(),
        "Get copied record from WIP when copy WIP to SOR");

    HttpResponse<Void> responseDelete = storageClient.deleteRecord(recordIdWipToSor);
    assertEquals(HttpStatus.SC_NO_CONTENT, responseDelete.statusCode(),
        "Check that record deleted when copy WIP to SOR");
  }

  @Test
  public void should_copyRecord_from_wip_to_wip() {
    assertRecordNotFound(recordIdWipToWip,
        collaborationHeaders(COLLABORATION_ID_WIP_TO_WIP, APPLICATION_NAME),
        "Check that record absent in target copy WIP to WIP");

    CopyRecordsRequest copyBody = getCopyRecordRequest(COLLABORATION_ID_WIP_TO_WIP, recordIdWipToWip);
    HttpResponse<Void> responseCopy = storageClient.copyRecords(copyBody,
        collaborationHeaders(COLLABORATION_ID, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, responseCopy.statusCode(), "Check response after copy WIP to WIP");

    HttpResponse<StorageRecord> getResponse = storageClient.getRecord(recordIdWipToWip,
        collaborationHeaders(COLLABORATION_ID_WIP_TO_WIP, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    StorageRecord copied = getResponse.body();
    assertEquals(recordIdWipToWip, copied.id(),
        "Get copied record from WIP when copy WIP to WIP");

    HttpResponse<Void> responseDelete = storageClient.deleteRecord(recordIdWipToWip,
        collaborationHeaders(COLLABORATION_ID_WIP_TO_WIP, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_NO_CONTENT, responseDelete.statusCode(),
        "Check that record deleted when copy WIP to WIP");
  }

  @Test
  public void should_return409_when_try_to_copy_sor_to_sor() {
    CopyRecordsRequest copyBody = getCopyRecordRequest("", recordIdWipToWip);
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.copyRecords(copyBody, collaborationHeadersWithoutId("", APPLICATION_NAME)));
    assertEquals(HttpStatus.SC_CONFLICT, ex.getStatusCode());
  }

  @Test
  public void should_return404_when_record_absent_in_source() {
    CopyRecordsRequest copyBody = getCopyRecordRequest(COLLABORATION_ID, recordIdWipToSor);
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.copyRecords(copyBody, collaborationHeadersWithoutId("", APPLICATION_NAME)));
    assertEquals(HttpStatus.SC_NOT_FOUND, ex.getStatusCode());
  }

  @Test
  public void should_return409_when_record_exist_in_target() {
    CopyRecordsRequest copyBody = getCopyRecordRequest("", recordIdExistInTarget);
    HttpResponse<Void> responseCopy = storageClient.copyRecords(copyBody,
        collaborationHeaders(COLLABORATION_ID, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_OK, responseCopy.statusCode(),
        "Check that record created in WIP when check exception about existing in target");

    CopyRecordsRequest copyBodyTest = getCopyRecordRequest("", recordIdExistInTarget);
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.copyRecords(copyBodyTest,
            collaborationHeaders(COLLABORATION_ID, APPLICATION_NAME)));
    assertEquals(HttpStatus.SC_CONFLICT, ex.getStatusCode(),
        "The already exists when check exception about existing in target");

    HttpResponse<Void> responseDelete = storageClient.deleteRecord(recordIdExistInTarget,
        collaborationHeaders(COLLABORATION_ID, APPLICATION_NAME));
    assertEquals(HttpStatus.SC_NO_CONTENT, responseDelete.statusCode(),
        "Check that record deleted when check exception about existing in target");
  }

  private void assertRecordNotFound(String recordId, Map<String, String> headers, String message) {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.getRecord(recordId, headers), message);
    assertEquals(HttpStatus.SC_NOT_FOUND, ex.getStatusCode(), message);
  }
}
