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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TestUtils;

public final class IngestRecordNotFoundTest extends BaseRecordsAcceptanceTest {

  private String LEGAL_TAG;
  private String KIND;
  private String RECORD_ID;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    LEGAL_TAG = getTenantId() + "-storage-" + now;
    KIND = getTenantId() + ":test:endtoend:1.1." + now;
    RECORD_ID = getTenantId() + ":endtoend:1.1." + now;
    createLegalTag(LEGAL_TAG);
  }

  @Test
  public void should_returnBadRequest_when_userGroupDoesNotExist() {
    String group = String.format("data.thisDataGrpDoesNotExsist@%s", getAclSuffix());
    StorageRecord[] records = withTestAcl(RecordUtil.replaceAcl(
        RecordUtil.createDefaultRecords(RECORD_ID, KIND, LEGAL_TAG),
        TestUtils.getAcl(), group));

    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.putRecords(records));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    assertEquals("Error on writing record", ex.getError().getReason());
    assertEquals("Could not find group \"" + group + "\".", ex.getError().getMessage());
  }
}
