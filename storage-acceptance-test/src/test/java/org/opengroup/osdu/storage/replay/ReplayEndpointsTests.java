// Copyright Â© Microsoft Corporation
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

package org.opengroup.osdu.storage.replay;

import org.opengroup.osdu.core.test.client.HttpResponse;

import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.QueryResult;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.common.base.Strings;

import java.util.Map;

import org.opengroup.osdu.core.test.client.model.replay.ReplayStartResponse;
import org.opengroup.osdu.core.test.client.model.replay.ReplayStatusResponse;
import org.opengroup.osdu.storage.model.search.SearchCountResponse;
import org.opengroup.osdu.core.test.client.model.storage.RecordAcl;
import org.opengroup.osdu.core.test.client.model.storage.RecordLegal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;
import org.opengroup.osdu.storage.util.ReplayUtils;

@Slf4j
public final class ReplayEndpointsTests extends BaseStorageAcceptanceTest {

  private String LEGAL_TAG_NAME;
  private String INVALID_KIND;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    assumeTrue(configUtils.isTestReplayEnabled());

    LEGAL_TAG_NAME = getTenantId() + "-storage-" + System.currentTimeMillis();
    INVALID_KIND = getTenantId() + ":ds:1.0." + System.currentTimeMillis();
    createLegalTag(LEGAL_TAG_NAME);
  }

  @Test
  public void should_return_400_when_givenNoOperationNameIsNotInRequest() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.startReplay(ReplayUtils.emptyReplayRequest()));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("Operation field is required. The valid operations are: 'replay', 'reindex'.",
        ex.getError().getMessage());
  }

  @Test
  public void should_return_400_when_givenKindIsEmpty() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.startReplay(ReplayUtils.replayRequest("reindex", new ArrayList<>())));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("Currently restricted to a single valid kind.", ex.getError().getMessage());
  }

  @Test
  public void should_return_400_when_givenKindSizeIsGreaterDenOne() throws Exception {
    List<String> kindList = new ArrayList<>();
    kindList.add(getKind());
    kindList.add(getKind());

    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.startReplay(ReplayUtils.replayRequest("reindex", kindList)));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("Currently restricted to a single valid kind.", ex.getError().getMessage());
  }

  @Test
  public void Should_return_400_when_givenInvalidKind() {
    List<String> kindList = new ArrayList<>();
    kindList.add(INVALID_KIND);
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.startReplay(ReplayUtils.replayRequest("reindex", kindList)));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("The requested kind does not exist.", ex.getError().getMessage());
  }

  @Test
  public void Should_return_400_when_givenInvalidOperationName() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.startReplay(ReplayUtils.replayRequest("invalidOperation")));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("Not a valid operation. The valid operations are: [reindex, replay]",
        ex.getError().getMessage());
  }

  @Test
  public void should_return_400_when_request_contains_unknown_properties() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.startReplay(ReplayUtils.replayRequestWithUnknownProperty()));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("Invalid replay request payload.", ex.getError().getMessage());
  }

  @Test
  public void should_return_200_GivenReplayAll() throws Exception {
    if (configUtils.getIsTestReplayAllEnabled()) {
      String kind_1 = getKind();
      String kind_2 = getKind();
      List<String> givenKindList = Arrays.asList(kind_1, kind_2);
      createTestRecordForGivenCapacityAndKinds(500, 100, givenKindList);

      var response = storageClient.queryKindsGet("?limit=10");
      assertEquals(HttpStatus.SC_OK, response.statusCode());
      QueryResult responseObject = response.body();
      List<String> kindList = new ArrayList<>(Arrays.asList(responseObject.results()));
      kindList.add(kind_1);
      kindList.add(kind_2);

      performValidationBeforeOrAfterReplay(kindList, givenKindList, "*:*:*:*", 2000);
      ReplayStatusResponse replayStatus =
          performReplay(ReplayUtils.replayRequest("reindex"));
      assertEquals("COMPLETED", replayStatus.status());
      assertNotNull(replayStatus.replayId());
      performValidationBeforeOrAfterReplay(kindList, givenKindList, "*:*:*:*", 2000);
    }
  }

  @Test
  @Timeout(2)
  public void should_return_200_givenSingleKind() throws Exception {
    if (configUtils.getIsTestReplayAllEnabled()) {
      String kind_1 = getKind();
      List<String> kindList = new ArrayList<>();
      kindList.add(kind_1);
      List<String> ids = createTestRecordForGivenCapacityAndKinds(1, 1, kindList);

      performValidationBeforeOrAfterReplay(kindList, kindList, kind_1, 1);
      ReplayStatusResponse replayStatus =
          performReplay(ReplayUtils.replayRequest("reindex", kindList));

      assertEquals("COMPLETED", replayStatus.status());
      assertNotNull(replayStatus.replayId());

      performValidationBeforeOrAfterReplay(kindList, kindList, kind_1, 1);
      deleteRecords(ids);
    }
  }

  public List<String> createTestRecordForGivenCapacityAndKinds(int n, int factor,
      List<String> kinds) throws Exception {
    List<String> totalIds = new ArrayList<>();
    for (String kind : kinds) {
      int counter = n;
      while (counter > 0) {
        List<String> listIds = create_N_TestRecordForGivenKind(factor, kind);
        totalIds = Stream.concat(totalIds.stream(), listIds.stream()).collect(Collectors.toList());
        counter -= factor;
        Thread.sleep(1000);
      }
    }

    Thread.sleep(40000);
    return totalIds;
  }

  @Test
  public void should_return_400_when_givenEmptyJSONIsSent() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.startReplay(ReplayUtils.emptyReplayRequest()));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  private List<String> create_N_TestRecordForGivenKind(int n, String kind) {
    List<String> ids = new ArrayList<>(n);
    StorageRecord[] records = new StorageRecord[n];
    for (int i = 0; i < n; i++) {
      String id1 = getTenantId() + ":inttest:" + System.currentTimeMillis() + i;
      records[i] = buildRecord(id1, "ash ketchum", kind, LEGAL_TAG_NAME);
      ids.add(id1);
    }

    var createResponse = storageClient.putRecords(records);

    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

    CreateRecordsResponse result = createResponse.body();
    assertEquals(n, result.recordCount());
    assertEquals(n, result.recordIds().length);
    assertEquals(n, result.recordIdVersions().length);

    return ids;
  }

  private void performValidationBeforeOrAfterReplay(List<String> kinds, List<String> givenKindList,
      String kindType, int totalReplayAllRecord) throws Exception {
    long startTime = System.currentTimeMillis();

    int initialRecordCount;
    int countNoOfAPICalls = 0;
    while ((initialRecordCount = getIndexedRecordCount(givenKindList)) != totalReplayAllRecord) {
      if (countNoOfAPICalls > 10) {
        fail();
      }

      Thread.sleep(configUtils.getTimeoutForReplay());
      countNoOfAPICalls++;
    }

    assertEquals(totalReplayAllRecord, initialRecordCount);

    log.info("Total count for Kind {} is {}", kindType, initialRecordCount);

    for (String kind : kinds) {
      sendDeleteIndex("index?kind=" + kind);
      Thread.sleep(1000);
    }

    int countOfRecord = getIndexedRecordCount(givenKindList);
    log.info("Total count for Kind {} after deletion is {}", kindType, countOfRecord);
    assertEquals(0, countOfRecord);

    log.info("The end time for performValidationBeforeOrAfterReplay for KindType {}is {}", kindType,
        System.currentTimeMillis() - startTime);
  }

  private ReplayStatusResponse performReplay(
      org.opengroup.osdu.core.test.client.model.replay.ReplayRequest request) throws Exception {
    HttpResponse<ReplayStartResponse> response;
    try {
      response = storageClient.startReplay(request);
    } catch (ClientException e) {
      if (e.getStatusCode() == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
        log.info("Error in replay call {}", e.getError().getMessage());
      }
      throw e;
    }

    assertEquals(202, response.statusCode());

    ReplayStartResponse startResponse = response.body();
    String replayId = startResponse.replayId();
    HttpResponse<ReplayStatusResponse> statusResponse =
        storageClient.getReplayStatus(replayId);
    assertEquals(HttpStatus.SC_OK, statusResponse.statusCode());

    ReplayStatusResponse replayStatus = statusResponse.body();
    log.info("Replay {} status: {}", replayId, replayStatus.status());

    int countNoOfAPICalls = 0;

    while (!"COMPLETED".equals(replayStatus.status())) {
      assertNotEquals("FAILED", replayStatus.status());
      statusResponse = storageClient.getReplayStatus(replayId);
      assertEquals(HttpStatus.SC_OK, statusResponse.statusCode());
      replayStatus = statusResponse.body();
      log.info("Replay {} status: {} ({})", replayId, replayStatus.status(), replayStatus.message());

      if (countNoOfAPICalls > 10) {
        fail();
      }

      Thread.sleep(configUtils.getTimeoutForReplay());
      countNoOfAPICalls++;
    }

    assertEquals(replayId, replayStatus.replayId());
    return replayStatus;
  }

  private void deleteRecords(List<String> ids) {
    long startTime = System.currentTimeMillis();

    ids.parallelStream().forEach((id) -> {
      try {
        storageClient.deleteRecord(id);
      } catch (Exception ignored) {
        // best-effort cleanup
      }
    });

    log.info("The totalTime for delete Records for size {}is {}", ids.size(),
        System.currentTimeMillis() - startTime);
  }

  private int getIndexedRecordCount(List<String> kinds) throws Exception {
    int recordCountIndexed = 0;
    for (String kind : kinds) {
      SearchCountResponse countResponse = sendSearchQuery(ReplayUtils.searchCountRequest(kind));
      recordCountIndexed += countResponse.totalCount();
    }
    return recordCountIndexed;
  }

  @Test
  public void should_return_404_when_givenInvalidReplayID() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.getReplayStatus("1234"));
    assertEquals(HttpStatus.SC_NOT_FOUND, ex.getStatusCode());
    assertEquals("The replay ID 1234 is invalid.", ex.getError().getMessage());
  }

  public String getKind() throws InterruptedException {
    Thread.sleep(1);
    return getTenantId() + ":ds:inttest:1.0." + System.nanoTime();
  }

  private StorageRecord buildRecord(String id, String name, String kind, String legalTagName) {
    RecordAcl acl = new RecordAcl(new String[] {getAcl()}, new String[] {getAcl()});
    RecordLegal legal = new RecordLegal(new String[] {legalTagName}, new String[] {"BR"});
    String recordId = Strings.isNullOrEmpty(id) ? null : id;
    return new StorageRecord(recordId, null, kind, acl, Map.of("name", name), legal, null, null, null,
        null, null, null, null);
  }
}
