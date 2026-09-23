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

package org.opengroup.osdu.storage.headervalidations;

import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;
import org.opengroup.osdu.storage.util.RecordUtil;

import java.util.Map;

public final class ValidateRequiredHeadersTest extends BaseStorageAcceptanceTest {

  private String kindOne;
  private String kindIdOne;
  private String legalTagName;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    long now = System.currentTimeMillis();
    kindOne = getTenantId() + ":test:endtoend:1.1." + now;
    kindIdOne = getTenantId() + ":endtoend:1.1." + now;
    legalTagName = getTenantId() + "-storage-" + System.currentTimeMillis();
    createLegalTag(legalTagName);
  }

  private void createTestRecordWithoutAuth(String kind, String id, String legalName) {
    StorageRecord[] jsonInputRecord = RecordUtil.createDefaultRecords(id, kind, legalName);
    storageClient.putRecords(jsonInputRecord, Map.of("Authorization", ""));
  }

  private void createTestRecordWithoutDataPartitionID(String kind, String id, String legalName) {
    StorageRecord[] jsonInputRecord = RecordUtil.createDefaultRecords(id, kind, legalName);
    storageClient.putRecords(jsonInputRecord, Map.of("data-partition-id", ""));
  }

  @Test
  public void ValidateMissingAuthHeaderReturnsUnauthorizedError() {
    ClientException ex = assertThrows(ClientException.class,
        () -> createTestRecordWithoutAuth(kindOne, kindIdOne, legalTagName));
    //validate that the error code is either 401/403 since for some its 403 I guess at some
    //other level like istio etc.
    assertTrue(ex.getStatusCode() == HttpStatus.SC_UNAUTHORIZED
        || ex.getStatusCode() == HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void ValidateMissingDataPartitionHeaderReturnsBadRequestError() {
    ClientException ex = assertThrows(ClientException.class,
        () -> createTestRecordWithoutDataPartitionID(kindOne, kindIdOne, legalTagName));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());

    AppError expectedError = new AppError(HttpStatus.SC_BAD_REQUEST, "Bad Request",
        "data-partition-id header is missing");

    assertEquals(expectedError, ex.getError());
  }
}
