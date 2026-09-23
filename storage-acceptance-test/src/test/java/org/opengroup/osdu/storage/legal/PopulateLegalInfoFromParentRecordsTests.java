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

package org.opengroup.osdu.storage.legal;

import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import static org.apache.hc.core5.http.HttpStatus.SC_BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opengroup.osdu.core.test.client.model.storage.RecordAcl;
import org.opengroup.osdu.core.test.client.model.storage.RecordAncestry;
import org.opengroup.osdu.core.test.client.model.storage.RecordLegal;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;

public final class PopulateLegalInfoFromParentRecordsTests extends BaseStorageAcceptanceTest {

  private static String KIND;
  private static String LEGAL_TAG_PARENT_ONE;
  private static String LEGAL_TAG_PARENT_TWO;
  private static String LEGAL_TAG_CHILD;
  private static String LEGAL_TAG_CHILD_THAT_WILL_NOT_BE_CREATED;
  private static String PARENT_ID_ONE;
  private static String PARENT_ID_TWO;
  private static String CHILD_ID;
  private static String CHILD_ID_THAT_IS_NOT_CREATED;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    KIND = getTenantId() + ":parent:inttest:1.0." + System.currentTimeMillis();

    LEGAL_TAG_PARENT_ONE = createLegalTagName("parent");
    Thread.sleep(1);
    LEGAL_TAG_PARENT_TWO = createLegalTagName("parent");
    LEGAL_TAG_CHILD = createLegalTagName("child");
    Thread.sleep(1);
    LEGAL_TAG_CHILD_THAT_WILL_NOT_BE_CREATED = createLegalTagName("child");
    PARENT_ID_ONE = getTenantId() + ":inttest:" + System.currentTimeMillis();
    Thread.sleep(1);
    PARENT_ID_TWO = getTenantId() + ":inttest:" + System.currentTimeMillis();
    CHILD_ID = getTenantId() + ":inttest:" + System.currentTimeMillis();
    Thread.sleep(1);
    CHILD_ID_THAT_IS_NOT_CREATED = getTenantId() + ":inttest:" + System.currentTimeMillis();

    createLegalTag(LEGAL_TAG_PARENT_ONE);
    createLegalTag(LEGAL_TAG_PARENT_TWO);
    createLegalTag(LEGAL_TAG_CHILD);

    createAndAssertRecord(PARENT_ID_ONE, LEGAL_TAG_PARENT_ONE, "parent1",
        Lists.newArrayList("BR", "IT"), null);
    createAndAssertRecord(PARENT_ID_TWO, LEGAL_TAG_PARENT_TWO, "parent2",
        Lists.newArrayList("DE", "DK"), null);
  }

  @Test
  public void should_appendOrdcAndLegalTagsWithParents_when_creatingRecordWithParentsSupplied() {
    StorageRecord parentRecord1 = retrieveRecord(PARENT_ID_ONE);
    StorageRecord parentRecord2 = retrieveRecord(PARENT_ID_TWO);

    createAndAssertRecord(CHILD_ID, LEGAL_TAG_CHILD, "chiiiiiild",
        Lists.newArrayList("FR", "US", "CA"),
        Lists.newArrayList(PARENT_ID_ONE + ":" + parentRecord1.version(),
            PARENT_ID_TWO + ":" + parentRecord2.version()));
    StorageRecord record = retrieveRecord(CHILD_ID);

    assertEquals(CHILD_ID, record.id());
    assertEquals(1, record.data().size());
    assertEquals("chiiiiiild", record.data().get("name"));
    assertNotNull(record.version());
    assertEquals(KIND, record.kind());
    assertArrayEquals(new String[] {getAcl()}, record.acl().viewers());
    assertArrayEquals(new String[] {getAcl()}, record.acl().owners());
    assertEquals(3, record.legal().legaltags().length);
    assertTrue(ArrayUtils.contains(record.legal().legaltags(), LEGAL_TAG_CHILD));
    assertTrue(ArrayUtils.contains(record.legal().legaltags(), LEGAL_TAG_PARENT_ONE));
    assertTrue(ArrayUtils.contains(record.legal().legaltags(), LEGAL_TAG_PARENT_TWO));
    assertTrue(ArrayUtils.contains(record.legal().otherRelevantDataCountries(), "BR"));
    assertTrue(ArrayUtils.contains(record.legal().otherRelevantDataCountries(), "IT"));
    assertTrue(ArrayUtils.contains(record.legal().otherRelevantDataCountries(), "FR"));
    assertTrue(ArrayUtils.contains(record.legal().otherRelevantDataCountries(), "US"));
    assertTrue(ArrayUtils.contains(record.legal().otherRelevantDataCountries(), "CA"));
    assertTrue(ArrayUtils.contains(record.legal().otherRelevantDataCountries(), "DE"));
    assertTrue(ArrayUtils.contains(record.legal().otherRelevantDataCountries(), "DK"));
    assertEquals(2, record.ancestry().parents().length);
    assertTrue(ArrayUtils.contains(record.ancestry().parents(),
        PARENT_ID_ONE + ":" + parentRecord1.version()));
    assertTrue(ArrayUtils.contains(record.ancestry().parents(),
        PARENT_ID_TWO + ":" + parentRecord2.version()));
  }

  @Test
  public void should_returnErrorCode400_when_anInvalidChildLegalTagProvided() {
    StorageRecord[] childBody = createRecords(CHILD_ID_THAT_IS_NOT_CREATED, "childname",
        Lists.newArrayList(LEGAL_TAG_CHILD_THAT_WILL_NOT_BE_CREATED), Lists.newArrayList("FR", "US", "CA"),
        null);

    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.putRecords(childBody));
    assertEquals(SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_return400_when_noParentRecordAndNoChildLegalTagsProvided() {
    StorageRecord[] body = createRecords(CHILD_ID_THAT_IS_NOT_CREATED, "childname", null,
        Lists.newArrayList("FR", "US"), null);
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.putRecords(body));
    assertEquals(SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_returnErrorCode400_when_noParentRecordAndNoORDCValuesProvided() {
    StorageRecord[] body = createRecords(CHILD_ID_THAT_IS_NOT_CREATED, "childname",
        Lists.newArrayList(LEGAL_TAG_PARENT_ONE), null, null);
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.putRecords(body));
    assertEquals(SC_BAD_REQUEST, ex.getStatusCode());
  }

  private StorageRecord retrieveRecord(String recordId) {
    var getResponse = storageClient.getRecord(recordId);
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    return getResponse.body();
  }

  private StorageRecord[] createRecords(String id, String dataValue, List<String> legalTags,
      List<String> ordc, List<String> parents) {
    RecordAcl acl = new RecordAcl(new String[] {getAcl()}, new String[] {getAcl()});
    String[] legalTagArray = legalTags != null ? legalTags.toArray(String[]::new) : null;
    String[] ordcArray = ordc != null ? ordc.toArray(String[]::new) : new String[0];
    RecordLegal legal = new RecordLegal(legalTagArray, ordcArray);
    RecordAncestry ancestry = parents != null && !parents.isEmpty()
        ? new RecordAncestry(parents.toArray(String[]::new)) : null;
    StorageRecord record = new StorageRecord(id, null, KIND, acl, Map.of("name", dataValue), legal, ancestry,
        null, null, null, null, null, null);
    return new StorageRecord[] {record};
  }

  private void createAndAssertRecord(String parentId, String legalTagForParent, String dataValue,
      ArrayList<String> ordc, List<String> parents) {
    StorageRecord[] body = createRecords(parentId, dataValue, Lists.newArrayList(legalTagForParent), ordc,
        parents);
    var createResponse = storageClient.putRecords(body);
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    CreateRecordsResponse result = createResponse.body();
    assertEquals(1, result.recordCount());
    assertEquals(1, result.recordIds().length);
    assertEquals(1, result.recordIdVersions().length);
    assertEquals(parentId, result.recordIds()[0]);
  }

}
