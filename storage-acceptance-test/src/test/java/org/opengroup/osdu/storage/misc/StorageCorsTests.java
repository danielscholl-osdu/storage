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

package org.opengroup.osdu.storage.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;

public final class StorageCorsTests extends BaseStorageAcceptanceTest {

  @Test
  @Disabled
  public void should_returnProperStatusCodeAndResponseHeaders_when_sendingPreflightOptionsRequest() {
    HttpResponse<Void> response = storageClient.queryKindsOptions("?limit=1");
    assertEquals(HttpStatus.SC_OK, response.statusCode());

    assertEquals("*", getHeader(response, "Access-Control-Allow-Origin"));
    assertEquals(
        "origin, content-type, accept, authorization, data-partition-id, correlation-id, appkey",
        getHeader(response, "Access-Control-Allow-Headers"));
    assertEquals("GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH",
        getHeader(response, "Access-Control-Allow-Methods"));
    assertEquals("true", getHeader(response, "Access-Control-Allow-Credentials"));
    assertEquals("DENY", getHeader(response, "X-Frame-Options"));
    assertEquals("1; mode=block", getHeader(response, "X-XSS-Protection"));
    assertEquals("nosniff", getHeader(response, "X-Content-Type-Options"));
    assertEquals("no-cache, no-store, must-revalidate", getHeader(response, "Cache-Control"));
    assertEquals("default-src 'self'", getHeader(response, "Content-Security-Policy"));
    String strictTransportSecurity = getHeader(response, "Strict-Transport-Security");
    assertNotNull(strictTransportSecurity);
    assertNotNull(strictTransportSecurity);
    assertTrue(strictTransportSecurity.contains("max-age=31536000"));
    assertTrue(strictTransportSecurity.contains("includeSubDomains"));
    assertEquals("0", getHeader(response, "Expires"));
    assertNotNull(getHeader(response, "correlation-id"));
  }

  private static String getHeader(HttpResponse<Void> response, String name) {
    for (Header header : response.headers()) {
      if (header.getName().equalsIgnoreCase(name)) {
        return header.getValue();
      }
    }
    return null;
  }
}
