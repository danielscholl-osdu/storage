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

package org.opengroup.osdu.storage.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

public final class SwaggerIntegrationTests extends TestBase {

    public static final String SWAGGER_API_PATH = "swagger-ui/index.html";
    public static final String SWAGGER_API_DOCS_PATH = "api-docs";

    @Override
    public void setup() throws Exception {
        // the test suite does not require pre-run configuration
    }

    @Override
    public void tearDown() throws Exception {
        // the test suite does not require post-run tear-down procedures
    }

    @Test
    public void shouldReturn200_whenSwaggerApiIsCalled() throws Exception {
        CloseableHttpResponse response = TestUtils
                .send(SWAGGER_API_PATH, "GET", new HashMap<>(), "", "");
        assertEquals(HttpStatus.SC_OK, response.getCode());
    }

    @Test
    public void shouldReturn200_whenSwaggerApiDocsIsCalled() throws Exception {
        CloseableHttpResponse response = TestUtils
                .send(SWAGGER_API_DOCS_PATH, "GET", new HashMap<>(), "", "");
        assertEquals(HttpStatus.SC_OK, response.getCode());
    }

}
