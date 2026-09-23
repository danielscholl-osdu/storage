/*
 * Copyright 2020-2026 Google LLC
 * Copyright 2020-2026 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.MultiRecordHeadersInfo;
import org.opengroup.osdu.core.test.client.model.storage.MultiRecordHeadersRequest;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.storage.records.BaseRecordsAcceptanceTest;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class PostQueryRecordsHeadersIntegrationTests extends BaseRecordsAcceptanceTest {

  private static final long NOW = System.currentTimeMillis();

  private static String RECORD_ID_PREFIX;
  private static String RECORD_ID;
  private static String KIND;
  private static String LEGAL_TAG;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    RECORD_ID_PREFIX = getTenantId() + ":query:";
    RECORD_ID = RECORD_ID_PREFIX + NOW;
    KIND = getTenantId() + ":ds:query:1.0." + NOW;
    LEGAL_TAG = getTenantId() + "-storage-" + NOW;

    createLegalTag(LEGAL_TAG);
    StorageRecord[] jsonInput = withTestAcl(RecordUtil.createDefaultRecords(3, RECORD_ID, KIND, LEGAL_TAG));

    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords(jsonInput);
    HttpResponse<CreateRecordsResponse> modifyRecordsResponse = storageClient.putRecords(jsonInput);
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());
    assertEquals(HttpStatus.SC_CREATED, modifyRecordsResponse.statusCode());
  }

  @Test
  public void should_returnSingleRecordHeadersMatching_when_givenIdAndNoAttributes() {
    var queryResponse = storageClient.queryRecordsHeadersPost(MultiRecordHeadersRequest.of(RECORD_ID + 0));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    MultiRecordHeadersInfo responseObject = queryResponse.body();
    assertNotNull(responseObject);
    assertEquals(1, responseObject.records().length);
    assertEquals(0, responseObject.notFound().length);
    assertEquals(0, responseObject.invalidRecords().length);

    var recordHeader = responseObject.records()[0];
    assertEquals(RECORD_ID + 0, recordHeader.id());
    assertEquals(KIND, recordHeader.kind());
    assertEquals(getAcl(), recordHeader.acl().viewers()[0]);
    assertNotNull(recordHeader.createUser());
    assertNotNull(recordHeader.createTime());
    assertNotNull(recordHeader.modifyUser());
    assertNotNull(recordHeader.modifyTime());
    assertNotNull(recordHeader.version());
  }

  @Test
  public void should_returnOnlyRequestedHeaderProperties_when_specificAttributesAreGiven() {
    var queryResponse = storageClient.queryRecordsHeadersPost(
        MultiRecordHeadersRequest.withAttributes(new String[]{"kind"}, RECORD_ID + 1)
    );
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    MultiRecordHeadersInfo responseObject = queryResponse.body();
    assertNotNull(responseObject);
    assertEquals(1, responseObject.records().length);

    var recordHeader = responseObject.records()[0];
    assertEquals(RECORD_ID + 1, recordHeader.id());
    assertEquals(KIND, recordHeader.kind());
    // Since we requested only "kind", non-requested properties should be null/omitted as per Jackson Include.NON_NULL setup.
    assertNull(recordHeader.acl());
    assertNull(recordHeader.legal());
    assertNull(recordHeader.createUser());
  }

  @Test
  public void should_returnMultipleRecordHeadersMatchingGivenIds_when_noAttributesAreGiven() {
    String recordN1 = RECORD_ID + 0;
    String recordN2 = RECORD_ID + 1;
    String recordN3 = RECORD_ID + 2;
    var queryResponse = storageClient.queryRecordsHeadersPost(
        MultiRecordHeadersRequest.of(recordN1, recordN2, recordN3)
    );
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    MultiRecordHeadersInfo responseObject = queryResponse.body();
    assertNotNull(responseObject);
    assertEquals(3, responseObject.records().length);
    assertEquals(0, responseObject.notFound().length);
    assertEquals(0, responseObject.invalidRecords().length);

    String[] ids = new String[]{
        responseObject.records()[0].id(),
        responseObject.records()[1].id(),
        responseObject.records()[2].id()
    };
    assertTrue(Arrays.asList(ids).contains(recordN1));
    assertTrue(Arrays.asList(ids).contains(recordN2));
    assertTrue(Arrays.asList(ids).contains(recordN3));
  }

  @Test
  public void should_returnNotFoundRecord_when_nonExistingIDGiven() {
    String notExistingId = RECORD_ID_PREFIX + "nonexisting:id";
    var queryResponse = storageClient.queryRecordsHeadersPost(MultiRecordHeadersRequest.of(notExistingId));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    MultiRecordHeadersInfo responseObject = queryResponse.body();
    assertNotNull(responseObject);
    assertEquals(0, responseObject.records().length);
    assertEquals(1, responseObject.notFound().length);
    assertEquals(notExistingId, responseObject.notFound()[0]);
    assertEquals(0, responseObject.invalidRecords().length);
  }

  @Test
  public void should_returnInvalidRecord_when_invalidIDGiven() {
    String invalidId = "invalid_id_format";
    var queryResponse = storageClient.queryRecordsHeadersPost(MultiRecordHeadersRequest.of(invalidId));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    MultiRecordHeadersInfo responseObject = queryResponse.body();
    assertNotNull(responseObject);
    assertEquals(0, responseObject.records().length);
    assertEquals(0, responseObject.notFound().length);
    assertEquals(1, responseObject.invalidRecords().length);
    assertEquals(invalidId, responseObject.invalidRecords()[0]);
  }

  @Test
  public void should_returnMixedResults_when_givenCombinationOfValidExistingValidNonExistingAndInvalidIds() {
    String existingId = RECORD_ID + 0;
    String nonExistingId = RECORD_ID_PREFIX + "nonexisting:id";
    String invalidId = "invalid_id_format";

    var queryResponse = storageClient.queryRecordsHeadersPost(
        MultiRecordHeadersRequest.of(existingId, nonExistingId, invalidId)
    );
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    MultiRecordHeadersInfo responseObject = queryResponse.body();
    assertNotNull(responseObject);

    // Verify found records
    assertEquals(1, responseObject.records().length);
    assertEquals(existingId, responseObject.records()[0].id());

    // Verify not found records
    assertEquals(1, responseObject.notFound().length);
    assertEquals(nonExistingId, responseObject.notFound()[0]);

    // Verify invalid records
    assertEquals(1, responseObject.invalidRecords().length);
    assertEquals(invalidId, responseObject.invalidRecords()[0]);
  }

  @Test
  public void should_returnMixedResultsWithProjection_when_givenCombinationOfValidAndInvalidIdsAndAttributes() {
    String existingId = RECORD_ID + 1;
    String nonExistingId = RECORD_ID_PREFIX + "nonexisting:projection";
    String invalidId = "invalid_id_projection";

    var queryResponse = storageClient.queryRecordsHeadersPost(
        MultiRecordHeadersRequest.withAttributes(new String[] {"kind"}, existingId, nonExistingId, invalidId)
    );
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());
    MultiRecordHeadersInfo responseObject = queryResponse.body();
    assertNotNull(responseObject);

    // Verify found records and their projection
    assertEquals(1, responseObject.records().length);
    var recordHeader = responseObject.records()[0];
    assertEquals(existingId, recordHeader.id());
    assertEquals(KIND, recordHeader.kind());
    assertNull(recordHeader.acl());
    assertNull(recordHeader.legal());

    // Verify not found records
    assertEquals(1, responseObject.notFound().length);
    assertEquals(nonExistingId, responseObject.notFound()[0]);

    // Verify invalid records
    assertEquals(1, responseObject.invalidRecords().length);
    assertEquals(invalidId, responseObject.invalidRecords()[0]);
  }

  @Test
  public void should_return400BadRequest_when_givenInvalidAttributeNames() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.queryRecordsHeadersPost(
            MultiRecordHeadersRequest.withAttributes(new String[]{"acll"}, RECORD_ID + 0)
        )
    );
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_return400BadRequest_when_givenAttributeNamesWithWhitespace() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.queryRecordsHeadersPost(
            MultiRecordHeadersRequest.withAttributes(new String[]{" version"}, RECORD_ID + 0)
        )
    );
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_return400BadRequest_when_givenEmptyRecordsList() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.queryRecordsHeadersPost(
            MultiRecordHeadersRequest.of()
        )
    );
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_return400BadRequest_when_givenTooManyRecords() {
    String[] records = new String[1001];
    Arrays.fill(records, RECORD_ID + 0);
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.queryRecordsHeadersPost(
            MultiRecordHeadersRequest.of(records)
        )
    );
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_return400BadRequest_when_givenTooManyAttributes() {
    String[] attributes = new String[]{
        "version", "kind", "acl", "legal", "ancestry",
        "tags", "createUser", "createTime", "modifyUser", "modifyTime", "version"
    };
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.queryRecordsHeadersPost(
            MultiRecordHeadersRequest.withAttributes(attributes, RECORD_ID + 0)
        )
    );
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }
}

