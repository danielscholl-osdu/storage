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

package org.opengroup.osdu.storage.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.opengroup.osdu.storage.records.BaseRecordsAcceptanceTest;
import org.opengroup.osdu.storage.util.RecordUtil;

/**
 * Shared setup for query integration tests that require five seeded records.
 */
abstract class BaseQueryRecordsAcceptanceTest extends BaseRecordsAcceptanceTest {

  protected static final long NOW = System.currentTimeMillis();

  protected static String RECORD_ID;
  protected static String KIND;
  protected static String LEGAL_TAG;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    RECORD_ID = getTenantId() + ":query:" + NOW;
    KIND = getTenantId() + ":ds:query:1.0." + NOW;
    LEGAL_TAG = getTenantId() + "-storage-" + NOW;

    createLegalTag(LEGAL_TAG);
    StorageRecord[] jsonInput = RecordUtil.createDefaultRecords(5, RECORD_ID, KIND, LEGAL_TAG);
    HttpResponse<CreateRecordsResponse> response = storageClient.putRecords(jsonInput);
    assertEquals(HttpStatus.SC_CREATED, response.statusCode());
  }

}
