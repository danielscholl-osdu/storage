// Copyright 2017-2023, Schlumberger
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
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.PatchOperation;
import org.opengroup.osdu.core.test.client.model.storage.QueryRecordsRequest;
import org.opengroup.osdu.core.test.client.model.storage.UpdateRecordsMetadataRequest;
import org.opengroup.osdu.core.test.client.model.storage.UpdateRecordsMetadataResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.RecordUtil;

import static org.junit.jupiter.api.Assertions.*;

public final class PatchRecordsTest extends BaseRecordsAcceptanceTest {

  private String LEGAL_TAG;
  private String LEGAL_TAG_TO_BE_PATCHED;
  private String KIND;
  private String KIND_TO_BE_PATCHED;
  private String RECORD_ID1;
  private String RECORD_ID2;
  private static final int MAX_OP_NUMBER = 100;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    LEGAL_TAG = createLegalTagName("");
    LEGAL_TAG_TO_BE_PATCHED = createLegalTagName("1");
    KIND = getTenantId() + ":bulkupdate:test:1.1." + now;
    KIND_TO_BE_PATCHED = getTenantId() + ":bulkupdate:test:1.2." + now;
    RECORD_ID1 = getTenantId() + ":test:1.1." + now;
    RECORD_ID2 = getTenantId() + ":test:1.2." + now;

    createLegalTag(LEGAL_TAG);
    createLegalTag(LEGAL_TAG_TO_BE_PATCHED);

    HttpResponse<CreateRecordsResponse> response =
        storageClient.putRecords(withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID1, KIND, LEGAL_TAG)));
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());

    response = storageClient.putRecords(withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID2, KIND, LEGAL_TAG)));
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());
  }

  @Test
  public void should_updateOnlyMetadata_whenOnlyMetadataIsPatched() {
    List<String> records = new ArrayList<>();
    records.add(RECORD_ID1);
    records.add(RECORD_ID2);
    ConvertedRecords queryResponseObject = fetchRecordsForIds(records);
    assertQueryResponse(queryResponseObject, 2);
    String currentVersionRecord1 = queryResponseObject.records()[0].version();
    String currentVersionRecord2 = queryResponseObject.records()[1].version();
    assertNull(queryResponseObject.records()[0].modifyTime());
    assertNull(queryResponseObject.records()[0].modifyUser());

    HttpResponse<UpdateRecordsMetadataResponse> patchResponse = storageClient.patchRecords(
        getPatchPayload(records, true, false),
        Map.of("Content-Type", "application/json-patch+json"));
    assertEquals(HttpStatus.SC_OK, patchResponse.statusCode());

    queryResponseObject = fetchRecordsForIds(records);
    assertEquals(currentVersionRecord1, queryResponseObject.records()[0].version());
    assertEquals(currentVersionRecord2, queryResponseObject.records()[1].version());
    assertEquals(2, queryResponseObject.records().length);
    assertEquals(KIND_TO_BE_PATCHED, queryResponseObject.records()[0].kind());
    assertEquals(KIND_TO_BE_PATCHED, queryResponseObject.records()[1].kind());
    assertEquals(getAcl(), queryResponseObject.records()[0].acl().viewers()[0]);
    assertEquals(getAcl(), queryResponseObject.records()[1].acl().viewers()[0]);
    assertEquals(getIntegrationTesterAcl(), queryResponseObject.records()[0].acl().owners()[0]);
    assertEquals(getIntegrationTesterAcl(), queryResponseObject.records()[1].acl().owners()[0]);
    assertTrue(
        Arrays.asList(queryResponseObject.records()[0].legal().legaltags()).contains(LEGAL_TAG));
    assertTrue(
        Arrays.asList(queryResponseObject.records()[1].legal().legaltags()).contains(LEGAL_TAG));
    assertTrue(Arrays.asList(queryResponseObject.records()[0].legal().legaltags())
        .contains(LEGAL_TAG_TO_BE_PATCHED));
    assertTrue(Arrays.asList(queryResponseObject.records()[1].legal().legaltags())
        .contains(LEGAL_TAG_TO_BE_PATCHED));
    Map<String, String> tags = queryResponseObject.records()[0].tags();
    assertTrue(tags.containsKey("tag1"));
    assertTrue(tags.containsKey("tag2"));
    assertEquals("value1", tags.get("tag1"));
    assertEquals("value2", tags.get("tag2"));
    tags = queryResponseObject.records()[1].tags();
    assertTrue(tags.containsKey("tag1"));
    assertTrue(tags.containsKey("tag2"));
    assertEquals("value1", tags.get("tag1"));
    assertEquals("value2", tags.get("tag2"));
  }

  @Test
  public void should_updateDataAndMetadataVersion_whenOnlyDataIsPatched() {
    List<String> records = new ArrayList<>();
    records.add(RECORD_ID1);
    ConvertedRecords queryResponseObject = fetchRecordsForIds(records);
    assertQueryResponse(queryResponseObject, 1);
    String currentVersionRecord1 = queryResponseObject.records()[0].version();

    HttpResponse<UpdateRecordsMetadataResponse> patchResponse = storageClient.patchRecords(
        getPatchPayload(records, false, true),
        Map.of("Content-Type", "application/json-patch+json"));
    assertEquals(HttpStatus.SC_OK, patchResponse.statusCode());

    queryResponseObject = fetchRecordsForIds(records);
    assertNotEquals(currentVersionRecord1, queryResponseObject.records()[0].version());
    assertEquals(KIND, queryResponseObject.records()[0].kind());
    assertTrue(queryResponseObject.records()[0].data().containsKey("data"));
    assertEquals(Map.of("message", "test data"),
        queryResponseObject.records()[0].data().get("data"));
    assertQueryResponse(queryResponseObject, 1);
  }

  @Test
  public void should_updateBothMetadataAndData_whenDataAndMetadataArePatched() {
    List<String> records = new ArrayList<>();
    records.add(RECORD_ID1);
    ConvertedRecords queryResponseObject = fetchRecordsForIds(records);
    assertQueryResponse(queryResponseObject, 1);
    String currentVersionRecord = queryResponseObject.records()[0].version();

    HttpResponse<UpdateRecordsMetadataResponse> patchResponse = storageClient.patchRecords(
        getPatchPayload(records, true, true),
        Map.of("Content-Type", "application/json-patch+json"));
    assertEquals(HttpStatus.SC_OK, patchResponse.statusCode());

    queryResponseObject = fetchRecordsForIds(records);
    assertEquals(1, queryResponseObject.records().length);
    assertNotEquals(currentVersionRecord, queryResponseObject.records()[0].version());
    assertEquals(KIND_TO_BE_PATCHED, queryResponseObject.records()[0].kind());
    assertEquals(getAcl(), queryResponseObject.records()[0].acl().viewers()[0]);
    assertEquals(getIntegrationTesterAcl(), queryResponseObject.records()[0].acl().owners()[0]);
    assertTrue(
        Arrays.asList(queryResponseObject.records()[0].legal().legaltags()).contains(LEGAL_TAG));
    assertTrue(Arrays.asList(queryResponseObject.records()[0].legal().legaltags())
        .contains(LEGAL_TAG_TO_BE_PATCHED));
    Map<String, String> tags = queryResponseObject.records()[0].tags();
    assertTrue(tags.containsKey("tag1"));
    assertTrue(tags.containsKey("tag2"));
    assertEquals("value1", tags.get("tag1"));
    assertEquals("value2", tags.get("tag2"));
    assertTrue(queryResponseObject.records()[0].data().containsKey("data"));
    assertEquals(Map.of("message", "test data"),
        queryResponseObject.records()[0].data().get("data"));
  }

  @Test
  public void should_update_whenNumberOfPatchOperationsIsMaximum() {
    List<String> records = new ArrayList<>();
    records.add(RECORD_ID1);
    ConvertedRecords queryResponseObject = fetchRecordsForIds(records);
    assertQueryResponse(queryResponseObject, 1);
    String currentVersionRecord = queryResponseObject.records()[0].version();

    HttpResponse<UpdateRecordsMetadataResponse> patchResponse = storageClient.patchRecords(
        getMaximumPatchOperationsPayload(records), Map.of("Content-Type", "application/json-patch+json"));
    assertEquals(HttpStatus.SC_OK, patchResponse.statusCode());

    queryResponseObject = fetchRecordsForIds(records);
    assertEquals(1, queryResponseObject.records().length);
    assertEquals(currentVersionRecord, queryResponseObject.records()[0].version());
    Map<String, String> tags = queryResponseObject.records()[0].tags();
    assertTrue(tags.containsKey("testTag0"));
    assertTrue(tags.containsKey("testTag99"));
    assertEquals("value0", tags.get("testTag0"));
    assertEquals("value99", tags.get("testTag99"));
  }

  private ConvertedRecords fetchRecordsForIds(List<String> recordIds) {
    var fetchResponse = storageClient.queryRecordsBatchPost(
        QueryRecordsRequest.of(recordIds.toArray(String[]::new)),
        Map.of("frame-of-reference", "none"));
    assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());
    return fetchResponse.body();
  }

  private void assertQueryResponse(ConvertedRecords queryResponse,
                                   int expectedRecordCount) {
    assertEquals(expectedRecordCount, queryResponse.records().length);
    assertEquals(getAcl(), queryResponse.records()[0].acl().viewers()[0]);
    assertEquals(getAcl(), queryResponse.records()[0].acl().owners()[0]);
  }

  private UpdateRecordsMetadataRequest getPatchPayload(List<String> records,
      boolean isMetaUpdate, boolean isDataUpdate) {
    List<PatchOperation> ops = new ArrayList<>();
    if (isMetaUpdate) {
      ops.add(getAddTagsPatchOp());
      ops.add(getReplaceAclOwnersPatchOp());
      ops.add(getAddLegaltagsPatchOp());
      ops.add(getReplaceKindPatchOp());
    }
    if (isDataUpdate) {
      ops.add(getReplaceDataPatchOp());
    }
    return RecordUtil.buildMetadataPatch(records.toArray(String[]::new),
        ops.toArray(PatchOperation[]::new));
  }

  private PatchOperation getAddTagsPatchOp() {
    Map<String, String> tagsValue = Map.of("tag1", "value1", "tag2", "value2");
    return new PatchOperation("add", "/tags", tagsValue);
  }

  private PatchOperation getReplaceAclOwnersPatchOp() {
    return new PatchOperation("replace", "/acl/owners",
        new String[] {getIntegrationTesterAcl()});
  }

  private PatchOperation getAddLegaltagsPatchOp() {
    return new PatchOperation("add", "/legal/legaltags/-", LEGAL_TAG_TO_BE_PATCHED);
  }

  private PatchOperation getReplaceKindPatchOp() {
    return new PatchOperation("replace", "/kind", KIND_TO_BE_PATCHED);
  }

  private PatchOperation getReplaceDataPatchOp() {
    Map<String, Object> innerDataValue = Map.of("message", "test data");
    Map<String, Object> newDataValue = Map.of("data", innerDataValue);
    return new PatchOperation("replace", "/data", newDataValue);
  }

  private UpdateRecordsMetadataRequest getMaximumPatchOperationsPayload(List<String> records) {
    PatchOperation[] ops = new PatchOperation[MAX_OP_NUMBER];
    for (int i = 0; i < MAX_OP_NUMBER; i++) {
      ops[i] = new PatchOperation("add", "/tags/testTag" + i, "value" + i);
    }
    return RecordUtil.buildMetadataPatch(records.toArray(String[]::new), ops);
  }
}
