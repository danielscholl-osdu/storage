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

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

public final class StorageCorsTests extends TestBase {

    @BeforeEach
    @Override
    public void setup() throws Exception {
        this.testUtils = new TokenTestUtils();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
    }

    @Test
    @Disabled
    public void should_returnProperStatusCodeAndResponseHeaders_when_sendingPreflightOptionsRequest() throws Exception {
        CloseableHttpResponse response = TestUtils.send("query/kinds", "OPTIONS", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=1");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        assertEquals("*", response.getHeaders("Access-Control-Allow-Origin")[0]);
        assertEquals(
                "origin, content-type, accept, authorization, data-partition-id, correlation-id, appkey",
                response.getHeaders("Access-Control-Allow-Headers")[0]);
        assertEquals("GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH",
                response.getHeaders("Access-Control-Allow-Methods")[0]);
        assertEquals("true", response.getHeaders("Access-Control-Allow-Credentials")[0]);
        assertEquals("DENY", response.getHeaders("X-Frame-Options")[0]);
        assertEquals("1; mode=block", response.getHeaders("X-XSS-Protection")[0]);
        assertEquals("nosniff", response.getHeaders("X-Content-Type-Options")[0]);
        assertEquals("no-cache, no-store, must-revalidate", response.getHeaders("Cache-Control")[0]);
        assertEquals("default-src 'self'", response.getHeaders("Content-Security-Policy")[0]);
        assertTrue(response.getHeaders("Strict-Transport-Security")[0].getValue().contains("max-age=31536000"));
        assertTrue(response.getHeaders("Strict-Transport-Security")[0].getValue().contains("includeSubDomains"));
        assertEquals("0", response.getHeaders("Expires")[0]);
        assertNotNull(response.getHeaders("correlation-id")[0]);
    }
}
