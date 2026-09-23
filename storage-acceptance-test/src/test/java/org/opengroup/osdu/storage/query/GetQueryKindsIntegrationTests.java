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

import  org.opengroup.osdu.core.test.client.model.storage.QueryResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;

public final class GetQueryKindsIntegrationTests extends BaseStorageAcceptanceTest {

  @Test
  public void should_returnMax1000Results_when_settingLimitToAValueLessThan1() {
    if (configUtils.getIsSchemaEndpointsEnabled()) {
      var response = storageClient.queryKindsGet("?limit=0");
      assertEquals(HttpStatus.SC_OK, response.statusCode());
      QueryResult responseObject = response.body();

      assertTrue(responseObject.results().length > 1 && responseObject.results().length <= 1000);
    }
  }

  @Test
  public void should_return400ErrorResult_when_givingAnInvalidCursorParameter() {
    if (configUtils.getIsSchemaEndpointsEnabled()) {
      ClientException ex = assertThrows(ClientException.class,
          () -> storageClient.queryKindsGet("?cursor=badCursorString"));
      assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
      assertEquals("Cursor invalid", ex.getError().getReason());
      assertEquals("The requested cursor does not exist or is invalid", ex.getError().getMessage());
    }
  }

  @Test
  public void should_return2Results_when_requesting2Items() {
    if (configUtils.getIsSchemaEndpointsEnabled()) {
      var response = storageClient.queryKindsGet("?limit=2");
      assertEquals(HttpStatus.SC_OK, response.statusCode());
      QueryResult responseObject = response.body();

      assertEquals(2, responseObject.results().length);
    }
  }

  @Test
  public void should_returnBadRequest_when_dataPartitionIDIsMissing() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.queryKindsGet("?limit=2", Map.of("data-partition-id", "")));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_returnNotFoundOrUnauthorized_when_dataPartitionIDIsInvalid() {
    String invalidTestDataPartitionId = "test-data-partition";
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.queryKindsGet("?limit=2",
            Map.of("data-partition-id", invalidTestDataPartitionId)));
    assertTrue(ex.getStatusCode() == HttpStatus.SC_UNAUTHORIZED
        || ex.getStatusCode() == HttpStatus.SC_NOT_FOUND
        || ex.getStatusCode() == HttpStatus.SC_FORBIDDEN);
  }
}
