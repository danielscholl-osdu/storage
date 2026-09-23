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

package org.opengroup.osdu.storage.misc;

import org.opengroup.osdu.core.test.client.HttpResponse;

import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;

import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.RecordAcl;
import org.opengroup.osdu.core.test.client.model.storage.RecordLegal;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;

@Slf4j
public final class StressTests extends BaseStorageAcceptanceTest {

  private static String LEGAL_TAG_NAME;
  private static String KIND;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    KIND = getTenantId() + ":ds:inttest:1.0." + System.currentTimeMillis();
    LEGAL_TAG_NAME = getTenantId() + "-storage-" + System.currentTimeMillis();

    createLegalTag(LEGAL_TAG_NAME);
  }

  @Test
  public void should_create100Records_when_givenValidRecord() {
    performanceTestCreateAndUpdateRecord(100);
  }

  @Test
  public void should_create10Records_when_givenValidRecord() {
    performanceTestCreateAndUpdateRecord(10);
  }

  @Test
  public void should_create1Records_when_givenValidRecord() {
    performanceTestCreateAndUpdateRecord(1);
  }

  private void performanceTestCreateAndUpdateRecord(int capacity) {
    List<String> ids = new ArrayList<>(capacity);
    StorageRecord[] records = new StorageRecord[capacity];
    for (int i = 0; i < capacity; i++) {
      String id = getTenantId() + ":inttest:" + System.currentTimeMillis() + i;
      records[i] = buildRecord(id, "ash ketchum", KIND, LEGAL_TAG_NAME);
      ids.add(id);
    }

    long startMillis = System.currentTimeMillis();
    var createResponse = storageClient.putRecords(records);
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    CreateRecordsResponse result = createResponse.body();
    long totalMillis = System.currentTimeMillis() - startMillis;
    log.info("Took {} milliseconds to Create {} 1KB records", totalMillis, ids.size());

    assertEquals(capacity, result.recordCount());
    assertEquals(capacity, result.recordIds().length);
    assertEquals(capacity, result.recordIdVersions().length);

    startMillis = System.currentTimeMillis();
    HttpResponse<CreateRecordsResponse> putResponse = storageClient.putRecords("?skipdupes=false", records);
    totalMillis = System.currentTimeMillis() - startMillis;
    assertEquals(HttpStatus.SC_CREATED, putResponse.statusCode());
    log.info("Took {} milliseconds to Update {} 1KB records", totalMillis, ids.size());

    startMillis = System.currentTimeMillis();
    putResponse = storageClient.putRecords("?skipdupes=false", records);
    totalMillis = System.currentTimeMillis() - startMillis;
    assertEquals(HttpStatus.SC_CREATED, putResponse.statusCode());
    log.info(
        "Took {} milliseconds to Update {} 1KB records when when skipdupes is true",
        totalMillis, ids.size());

    startMillis = System.currentTimeMillis();
    HttpResponse<StorageRecord> getResponse = storageClient.getRecord(ids.get(0));
    totalMillis = System.currentTimeMillis() - startMillis;
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    log.info("Took {} milliseconds to GET 1 1KB record", totalMillis);

    ids.parallelStream().forEach(id -> {
      try {
        storageClient.deleteRecord(id);
      } catch (Exception ignored) {
        // best-effort cleanup for performance test records
      }
    });
  }

  private StorageRecord buildRecord(String id, String name, String kind, String legalTagName) {
    RecordAcl acl = new RecordAcl(new String[] {getAcl()}, new String[] {getAcl()});
    RecordLegal legal = new RecordLegal(new String[] {legalTagName}, new String[] {"BR"});
    String recordId = Strings.isNullOrEmpty(id) ? null : id;
    return new StorageRecord(recordId, null, kind, acl, Map.of("name", name), legal, null, null, null,
        null, null, null, null);
  }
}
