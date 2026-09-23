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
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.storage.ConvertedRecords;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.PatchOperation;
import org.opengroup.osdu.core.test.client.model.storage.QueryRecordsRequest;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.UpdateRecordsMetadataRequest;
import org.opengroup.osdu.core.test.client.model.storage.UpdateRecordsMetadataResponse;

import static org.apache.hc.core5.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static org.apache.hc.core5.http.HttpStatus.SC_PARTIAL_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.RecordUtil;

@Slf4j
public final class UpdateRecordsMetadataTest extends BaseRecordsAcceptanceTest {
  static final String TAG_KEY = "tagkey1";
  static final String TAG_VALUE1 = "tagvalue1";
  private static final String TAG_VALUE2 = "tagvalue2";

  private String ACL;
  private String ACL_2;
  private String LEGAL_TAG;
  private String LEGAL_TAG_2;
  private String RECORD_ID;
  private String RECORD_ID_2;
  private String RECORD_ID_3;
  private String RECORD_ID_4;
  private String NOT_EXISTED_RECORD_ID;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    ACL = getIntegrationTesterAcl();
    ACL_2 = getAcl();
    LEGAL_TAG = createLegalTagName("");
    LEGAL_TAG_2 = createLegalTagName("2");
    String LEGAL_TAG_3 = createLegalTagName("3");
    String LEGAL_TAG_4 = createLegalTagName("4");
    String KIND = getTenantId() + ":bulkupdate:test:1.1." + now;
    RECORD_ID = getTenantId() + ":test:1.1." + now;
    RECORD_ID_2 = getTenantId() + ":test:1.2." + now;
    RECORD_ID_3 = getTenantId() + ":test:1.3." + now;
    RECORD_ID_4 = getTenantId() + ":test:1.4." + now;
    NOT_EXISTED_RECORD_ID = getTenantId() + ":bulkupdate:1.6." + now;

    createLegalTag(LEGAL_TAG);
    createLegalTag(LEGAL_TAG_2);
    createLegalTag(LEGAL_TAG_3);
    createLegalTag(LEGAL_TAG_4);

    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords(
        withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID, KIND, LEGAL_TAG)));
    HttpResponse<CreateRecordsResponse> response2 = storageClient.putRecords(
        withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID_2, KIND, LEGAL_TAG_2)));
    HttpResponse<CreateRecordsResponse> response3 = storageClient.putRecords(
        withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID_3, KIND, LEGAL_TAG_3)));
    HttpResponse<CreateRecordsResponse> response4 = storageClient.putRecords(
        withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID_4, KIND, LEGAL_TAG_4)));
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());
    assertEquals(HttpStatus.SC_CREATED, response2.statusCode());
    assertEquals(HttpStatus.SC_CREATED, response3.statusCode());
    assertEquals(HttpStatus.SC_CREATED, response4.statusCode());
  }

  @Test
  public void should_return200andUpdateMetadata_whenValidRecordsProvided() {
    var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(RECORD_ID, RECORD_ID_2), Map.of("frame-of-reference", "none"));
    assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());
    ConvertedRecords queryResponseObject1 = fetchResponse.body();
    assertEquals(2, queryResponseObject1.records().length);
    assertEquals(getAcl(), queryResponseObject1.records()[0].acl().viewers()[0]);
    assertEquals(getAcl(), queryResponseObject1.records()[0].acl().owners()[0]);

    UpdateRecordsMetadataRequest updateBody = RecordUtil.buildMetadataPatch(
        new String[] {RECORD_ID, RECORD_ID_2},
        new PatchOperation("replace", "/acl/viewers", new String[] {getIntegrationTesterAcl()}),
        new PatchOperation("replace", "/acl/owners", new String[] {getIntegrationTesterAcl()}));

    HttpResponse<UpdateRecordsMetadataResponse> bulkUpdateResponse = storageClient.patchRecords(updateBody);
    assertEquals(HttpStatus.SC_OK, bulkUpdateResponse.statusCode());

    var fetchResponse2 = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(RECORD_ID, RECORD_ID_2), Map.of("frame-of-reference", "none"));
    assertEquals(HttpStatus.SC_OK, fetchResponse2.statusCode());
    ConvertedRecords queryResponseObject2 = fetchResponse2.body();
    assertEquals(2, queryResponseObject2.records().length);
    assertEquals(getIntegrationTesterAcl(), queryResponseObject2.records()[0].acl().viewers()[0]);
    assertEquals(getIntegrationTesterAcl(), queryResponseObject2.records()[0].acl().owners()[0]);
  }

  @Test
  public void should_return206andUpdateMetadata_whenOneRecordProvided() {
    var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(RECORD_ID_3, RECORD_ID_4),
        Map.of("slb-frame-of-reference", "none"));
    assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());
    ConvertedRecords queryResponseObject1 = fetchResponse.body();
    assertEquals(2, queryResponseObject1.records().length);
    assertEquals(getAcl(), queryResponseObject1.records()[0].acl().viewers()[0]);
    assertEquals(getAcl(), queryResponseObject1.records()[0].acl().owners()[0]);

    UpdateRecordsMetadataRequest updateBody = RecordUtil.buildMetadataPatch(
        new String[] {RECORD_ID_3, RECORD_ID_4, getTenantId() + ":not:found"},
        new PatchOperation("replace", "/acl/viewers", new String[] {getIntegrationTesterAcl()}),
        new PatchOperation("replace", "/acl/owners", new String[] {getIntegrationTesterAcl()}));

    HttpResponse<UpdateRecordsMetadataResponse> bulkUpdateResponse = storageClient.patchRecords(updateBody);
    assertEquals(HttpStatus.SC_PARTIAL_CONTENT, bulkUpdateResponse.statusCode());

    var fetchResponse2 = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(RECORD_ID_3, RECORD_ID_4),
        Map.of("slb-frame-of-reference", "none"));
    assertEquals(HttpStatus.SC_OK, fetchResponse2.statusCode());
    ConvertedRecords queryResponseObject2 = fetchResponse2.body();
    assertEquals(2, queryResponseObject2.records().length);
    assertEquals(getIntegrationTesterAcl(), queryResponseObject2.records()[0].acl().viewers()[0]);
    assertEquals(getIntegrationTesterAcl(), queryResponseObject2.records()[0].acl().owners()[0]);
  }

  @Test
  public void should_return200AndUpdateTagsMetadata_whenValidRecordsProvided() {
    UpdateRecordsMetadataRequest updateBody = RecordUtil.buildUpdateTagsMetadata(RECORD_ID, "add",
        TAG_KEY + ":" + TAG_VALUE1);

    HttpResponse<UpdateRecordsMetadataResponse> updateResponse = storageClient.patchRecords(updateBody);
    assertEquals(SC_OK, updateResponse.statusCode());

    UpdateRecordsMetadataResponse patchResult = updateResponse.body();
    assertEquals(RECORD_ID, patchResult.recordIds()[0]);

    var getResponse = storageClient.getRecord(RECORD_ID);

    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());

    StorageRecord record = getResponse.body();
    assertEquals(TAG_VALUE1, record.tags().get(TAG_KEY));

    updateBody = RecordUtil.buildUpdateTagsMetadata(RECORD_ID, "replace", TAG_KEY + ":" + TAG_VALUE2);
    storageClient.patchRecords(updateBody);
    getResponse = storageClient.getRecord(RECORD_ID);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    record = getResponse.body();
    assertEquals(TAG_VALUE2, record.tags().get(TAG_KEY));

    updateBody = RecordUtil.buildUpdateTagsMetadata(RECORD_ID, "remove", TAG_KEY);
    storageClient.patchRecords(updateBody);
    getResponse = storageClient.getRecord(RECORD_ID);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    record = getResponse.body();
    assertNull(record.tags());
  }

  @Test
  public void should_return206andUpdateTagsMetadata_whenNotExistedRecordProvided() {
    UpdateRecordsMetadataRequest updateBody = RecordUtil.buildUpdateTagsMetadata(
        NOT_EXISTED_RECORD_ID, "replace", TAG_KEY + ":" + TAG_VALUE1);

    HttpResponse<UpdateRecordsMetadataResponse> updateResponse = storageClient.patchRecords(updateBody);
    assertEquals(SC_PARTIAL_CONTENT, updateResponse.statusCode());
    UpdateRecordsMetadataResponse resultObject = updateResponse.body();

    log.info("{}", resultObject);
    assertEquals(NOT_EXISTED_RECORD_ID, resultObject.notFoundRecordIds()[0]);
  }

  @Test
  public void should_return200AndUpdateLegalMetadataOr400ForRemoveRestriction_whenValidRecordsProvided() {
    UpdateRecordsMetadataRequest updateBody = RecordUtil.buildUpdateLegalMetadata(RECORD_ID, "add",
        LEGAL_TAG);

    HttpResponse<UpdateRecordsMetadataResponse> updateResponse = storageClient.patchRecords(updateBody);
    assertEquals(SC_OK, updateResponse.statusCode());
    assertEquals(SC_OK, storageClient.getRecord(RECORD_ID).statusCode());

    UpdateRecordsMetadataResponse patchResult = updateResponse.body();
    assertEquals(RECORD_ID, patchResult.recordIds()[0]);

    var getResponse = storageClient.getRecord(RECORD_ID);

    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());

    StorageRecord record = getResponse.body();
    assertEquals(LEGAL_TAG, record.legal().legaltags()[0]);

    updateBody = RecordUtil.buildUpdateLegalMetadata(RECORD_ID, "replace", LEGAL_TAG_2);
    storageClient.patchRecords(updateBody);
    getResponse = storageClient.getRecord(RECORD_ID);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    record = getResponse.body();
    assertEquals(LEGAL_TAG_2, record.legal().legaltags()[0]);

    UpdateRecordsMetadataRequest removeBody = RecordUtil.buildUpdateLegalMetadata(RECORD_ID,
        "remove", LEGAL_TAG_2);
    ClientException removeLegalError = assertThrows(ClientException.class,
        () -> storageClient.patchRecords(removeBody));
    assertEquals(SC_BAD_REQUEST, removeLegalError.getStatusCode());
  }

  @Test
  public void should_return200AndUpdateAclViewersMetadataOr400ForRemoveRestriction_whenValidRecordsProvided() {
    UpdateRecordsMetadataRequest updateBody = RecordUtil.buildUpdateAclMetadata(RECORD_ID, "add",
        "/acl/viewers", ACL);

    HttpResponse<UpdateRecordsMetadataResponse> updateResponse = storageClient.patchRecords(updateBody);
    assertEquals(SC_OK, updateResponse.statusCode());
    assertEquals(SC_OK, storageClient.getRecord(RECORD_ID).statusCode());

    UpdateRecordsMetadataResponse patchResult = updateResponse.body();
    assertEquals(RECORD_ID, patchResult.recordIds()[0]);

    var getResponse = storageClient.getRecord(RECORD_ID);

    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());

    StorageRecord record = getResponse.body();
    assertEquals(ACL, record.acl().viewers()[0]);

    updateBody = RecordUtil.buildUpdateAclMetadata(RECORD_ID, "replace", "/acl/viewers", ACL_2);
    storageClient.patchRecords(updateBody);
    getResponse = storageClient.getRecord(RECORD_ID);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    record = getResponse.body();
    assertEquals(ACL_2, record.acl().viewers()[0]);

    UpdateRecordsMetadataRequest removeBody = RecordUtil.buildUpdateAclMetadata(RECORD_ID, "remove",
        "/acl/viewers", ACL_2);
    ClientException removeViewersError = assertThrows(ClientException.class,
        () -> storageClient.patchRecords(removeBody));
    assertEquals(SC_BAD_REQUEST, removeViewersError.getStatusCode());
  }

  @Test
  public void should_return200AndUpdateAclOwnersMetadataOr400ForRemoveRestriction_whenValidRecordsProvided() {
    UpdateRecordsMetadataRequest addBody = RecordUtil.buildUpdateAclMetadata(RECORD_ID, "add",
        "/acl/owners", ACL);

    HttpResponse<UpdateRecordsMetadataResponse> updateResponse = storageClient.patchRecords(
        addBody);
    assertEquals(SC_OK, updateResponse.statusCode());
    assertEquals(SC_OK, storageClient.getRecord(RECORD_ID).statusCode());

    UpdateRecordsMetadataResponse patchResult = updateResponse.body();
    assertEquals(RECORD_ID, patchResult.recordIds()[0]);

    var getResponse = storageClient.getRecord(RECORD_ID);

    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());

    StorageRecord record = getResponse.body();
    assertEquals(ACL, record.acl().owners()[0]);

    UpdateRecordsMetadataRequest removeBody = RecordUtil.buildUpdateAclMetadata(RECORD_ID, "remove",
        "/acl/owners", ACL_2);
    storageClient.patchRecords(removeBody);
    getResponse = storageClient.getRecord(RECORD_ID);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    record = getResponse.body();
    assertEquals(ACL, record.acl().owners()[0]);

    UpdateRecordsMetadataRequest replaceBody = RecordUtil.buildUpdateAclMetadata(RECORD_ID,
        "replace", "/acl/owners ", ACL);
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.patchRecords(replaceBody));
    assertEquals(SC_BAD_REQUEST, ex.getStatusCode());
    getResponse = storageClient.getRecord(RECORD_ID);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    record = getResponse.body();
    assertEquals(ACL, record.acl().owners()[0]);
  }

  @Test
  public void should_return206andUpdateLegalMetadata_whenNotExistedRecordProvided() {
    UpdateRecordsMetadataRequest updateBody = RecordUtil.buildUpdateLegalMetadata(
        NOT_EXISTED_RECORD_ID, "replace", LEGAL_TAG);

    HttpResponse<UpdateRecordsMetadataResponse> updateResponse = storageClient.patchRecords(updateBody);
    assertEquals(SC_PARTIAL_CONTENT, updateResponse.statusCode());
    UpdateRecordsMetadataResponse resultObject = updateResponse.body();

    log.info("{}", resultObject);
    assertEquals(NOT_EXISTED_RECORD_ID, resultObject.notFoundRecordIds()[0]);
  }

  @Test
  public void should_return206andUpdateAclMetadata_whenNotExistedRecordProvided() {
    UpdateRecordsMetadataRequest updateBody = RecordUtil.buildUpdateAclMetadata(
        NOT_EXISTED_RECORD_ID, "replace", "/acl/viewers", ACL);

    HttpResponse<UpdateRecordsMetadataResponse> updateResponse = storageClient.patchRecords(updateBody);
    assertEquals(SC_PARTIAL_CONTENT, updateResponse.statusCode());
    UpdateRecordsMetadataResponse resultObject = updateResponse.body();

    log.info("{}", resultObject);
    assertEquals(NOT_EXISTED_RECORD_ID, resultObject.notFoundRecordIds()[0]);
  }
}
