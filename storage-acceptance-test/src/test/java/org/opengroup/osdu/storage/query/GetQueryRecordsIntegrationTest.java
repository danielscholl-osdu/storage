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

import org.junit.jupiter.api.Assertions;
import org.opengroup.osdu.core.test.client.model.storage.QueryResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.ClientException;

public final class GetQueryRecordsIntegrationTest extends BaseQueryRecordsAcceptanceTest {

  @Test
  public void should_return5Ids_when_requestingKindThatHas5Entries() {
    var response = storageClient.queryRecordsGet("?kind=" + KIND);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    QueryResult responseObject = response.body();
    assertEquals(5, responseObject.results().length);
  }

  @Test
  public void should_incrementThroughIds_when_requestingKindThatHasMoreThanOneEntry_and_limitIsSetTo2_and_usingPreviousCursorPos() {

    Set<String> result = new HashSet<>();

    HttpResponse<QueryResult> response = queryRecordsOrFail("?limit=2&kind=" + KIND);
    Assertions.assertNotNull(response);
    Assertions.assertNotNull(response);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    QueryResult responseObject = response.body();
    assertEquals(2, responseObject.results().length);
    assertFalse(StringUtils.isEmpty(responseObject.cursor()));

    result.add(responseObject.results()[0]);
    result.add(responseObject.results()[1]);

    String cursor = responseObject.cursor();

    response = queryRecordsOrFail("?limit=2&cursor=" + cursor + "&kind=" + KIND);
    Assertions.assertNotNull(response);
    Assertions.assertNotNull(response);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    responseObject = response.body();
    assertEquals(2, responseObject.results().length);
    assertFalse(StringUtils.isEmpty(responseObject.cursor()));

    result.add(responseObject.results()[0]);
    result.add(responseObject.results()[1]);

    cursor = responseObject.cursor();

    response = queryRecordsOrFail("?limit=2&cursor=" + cursor + "&kind=" + KIND);
    Assertions.assertNotNull(response);
    Assertions.assertNotNull(response);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    responseObject = response.body();
    assertEquals(1, responseObject.results().length);

    result.add(responseObject.results()[0]);

    assertEquals(5, result.size());
  }

  @Test
  public void should_returnError400_when_usingKindThatHasBadFormat() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.queryRecordsGet("?limit=1&kind=bad:kind"));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_returnNoResults_when_usingKindThatDoesNotExist() {
    var response = storageClient.queryRecordsGet("?limit=1&kind=nonexisting:kind:formatted:1.0.0");
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    QueryResult responseObject = response.body();

    assertEquals(0, responseObject.results().length);
  }

  @Test
  public void should_returnError400_when_usingInvalidCursorParameter() {
    String kind = getTenantId() + ":storage:inttest:1.0.0" + System.currentTimeMillis();
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.queryRecordsGet("?limit=1&cursor=MY_BAD_CURSOR&kind=" + kind));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  public void should_returnError400_when_notProvidingKindParameter() {
    ClientException ex = assertThrows(ClientException.class,
        () -> storageClient.queryRecordsGet("?limit=1&kind="));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());

    ex = assertThrows(ClientException.class,
        () -> storageClient.queryRecordsGet("?limit=1"));
    assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
  }

  private HttpResponse<QueryResult> queryRecordsOrFail(String query) {
    try {
      return storageClient.queryRecordsGet(query);
    } catch (ClientException e) {
      fail(formResponseCheckingMessage(e));
      return null;
    }
  }

  private String formResponseCheckingMessage(ClientException ex) {
    return "API is not acting properly, response code is: " + ex.getStatusCode()
        + ". And the reason is: " + ex.getError().getReason();
  }
}
