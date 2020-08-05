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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.HttpStatus;
import org.junit.Test;

import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import com.sun.jersey.api.client.ClientResponse;

public abstract class StorageCorsTests extends TestBase {

    @Test
    public void should_returnProperStatusCodeAndResponseHeaders_when_sendingPreflightOptionsRequest() throws Exception {
        ClientResponse response = TestUtils.send("query/kinds", "OPTIONS", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=1");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        MultivaluedMap<String, String> headers = response.getHeaders();

        assertEquals("[*]", headers.get("Access-Control-Allow-Origin").get(0));
        assertEquals(
                "[origin, content-type, accept, authorization, data-partition-id, correlation-id, appkey]",
                headers.get("Access-Control-Allow-Headers").get(0));
        assertEquals("[GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH]",
                headers.get("Access-Control-Allow-Methods").get(0));
        assertEquals("[true]", headers.get("Access-Control-Allow-Credentials").get(0));
        assertEquals("DENY", headers.get("X-Frame-Options").get(0));
        assertEquals("1; mode=block", headers.get("X-XSS-Protection").get(0));
        assertEquals("nosniff", headers.get("X-Content-Type-Options").get(0));
        assertEquals("[no-cache, no-store, must-revalidate]", headers.get("Cache-Control").get(0));
        assertEquals("[default-src 'self']", headers.get("Content-Security-Policy").get(0));
        assertEquals("[max-age=31536000; includeSubDomains]", headers.get("Strict-Transport-Security").get(0));
        assertEquals("[0]", headers.get("Expires").get(0));
        assertNotNull(headers.get("correlation-id").get(0));
    }
}