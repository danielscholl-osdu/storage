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
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;

import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.RecordUtil;

public final class GetRecordsIntegrationTest extends BaseRecordsAcceptanceTest {

  private String RECORD_ID;
  private String ANOTHER_RECORD_ID;
  private String KIND;
  private String LEGAL_TAG_NAME_A;
  private String LEGAL_TAG_NAME_B;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    RECORD_ID = getTenantId() + ":getrecord:" + now;
    ANOTHER_RECORD_ID = getTenantId() + ":getrecordnodup:" + now;
    KIND = getTenantId() + ":ds:getrecord:1.0." + now;
    LEGAL_TAG_NAME_A = getTenantId() + "-storage-a-" + now;
    LEGAL_TAG_NAME_B = getTenantId() + "-storage-b-" + now;

    createLegalTag(LEGAL_TAG_NAME_A);
    Thread.sleep(100);
    createLegalTag(LEGAL_TAG_NAME_B);

    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords(withTestAcl(RecordUtil.createDefaultRecords(RECORD_ID, KIND, LEGAL_TAG_NAME_A)));
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());
  }

  @Test
  public void should_getRecord_when_validRecordIdIsProvided() {
    var getResponse = storageClient.getRecord(RECORD_ID);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    StorageRecord record = getResponse.body();
    assertEquals(RECORD_ID, record.id());
    assertEquals(KIND, record.kind());
    assertEquals(getAcl(), record.acl().owners()[0]);
    assertEquals(getAcl(), record.acl().viewers()[0]);

    @SuppressWarnings("unchecked")
    Map<String, Object> intTag = (Map<String, Object>) record.data().get("int-tag");
    @SuppressWarnings("unchecked")
    Map<String, Object> doubleTag = (Map<String, Object>) record.data().get("double-tag");
    assertEquals(58377304471659395L, intTag.get("score-int"));
    assertEquals(58377304.471659395, doubleTag.get("score-double"));
    assertEquals(123456789L, record.data().get("count"));
  }

  @Test
  public void should_getRecord_withoutDuplicates_when_duplicateAclAndLegaltagsAreProvided() {
    StorageRecord[] jsonInputWithDuplicates = withTestAcl(
        RecordUtil.createRecordsWithDuplicateAclAndLegaltags(ANOTHER_RECORD_ID, KIND,
            LEGAL_TAG_NAME_A));
    HttpResponse<CreateRecordsResponse> putResponse = storageClient.putRecords(jsonInputWithDuplicates);
    assertEquals(HttpStatus.SC_CREATED, putResponse.statusCode());

    var getResponse = storageClient.getRecord(ANOTHER_RECORD_ID);

    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());

    StorageRecord record = getResponse.body();
    assertEquals(ANOTHER_RECORD_ID, record.id());
    assertEquals(KIND, record.kind());
    assertEquals(LEGAL_TAG_NAME_A, record.legal().legaltags()[0]);
    assertEquals(getAcl(), record.acl().owners()[0]);
    assertEquals(getAcl(), record.acl().viewers()[0]);
  }

  @Test
  public void should_getOnlyTheCertainDataFields_when_attributesAreProvided() {
    var getResponse = storageClient.getRecord(RECORD_ID,
        "?attribute=data.count&attribute=data.int-tag.score-int");
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    StorageRecord record = getResponse.body();
    assertEquals(RECORD_ID, record.id());
    assertEquals(KIND, record.kind());
    assertEquals(getAcl(), record.acl().owners()[0]);
    assertEquals(getAcl(), record.acl().viewers()[0]);

    assertEquals(58377304471659395L, record.data().get("int-tag.score-int"));
    assertNull(record.data().get("double-tag"));
    assertEquals(123456789L, record.data().get("count"));
  }

  @Test
  public void should_notReturnFieldsAlreadyInDatastore_when_returningRecord() {
    var getResponse = storageClient.getRecord(RECORD_ID);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    StorageRecord record = getResponse.body();
    assertNotNull(record.id());
    assertNotNull(record.kind());
    assertNotNull(record.acl());
    assertNotNull(record.version());
    assertNotNull(record.data());
    assertNotNull(record.createTime());

    assertNull(record.meta());
    assertNull(record.tags());
    assertNull(record.modifyUser());
    assertNull(record.modifyTime());
  }

  @Test
  public void should_legaltagChange_when_updateRecordWithLegaltag() {
    StorageRecord[] newJsonInput = withTestAcl(
        RecordUtil.createDefaultRecords(RECORD_ID, KIND, LEGAL_TAG_NAME_B));
    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords("?skipdupes=false", newJsonInput);
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());

    var getResponse = storageClient.getRecord(RECORD_ID);

    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());

    StorageRecord record = getResponse.body();
    assertEquals(RECORD_ID, record.id());
    assertEquals(KIND, record.kind());
    assertEquals(1, record.legal().legaltags().length);
    assertEquals(LEGAL_TAG_NAME_B, record.legal().legaltags()[0]);
  }
}
