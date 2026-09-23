/*
 * Copyright 2020-2023 Google LLC
 * Copyright 2021-2023 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.api;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import static org.junit.Assert.assertEquals;

public abstract class HealthCheckApiIntegrationTest extends TestBase {

  @Test
  public void should_returnOk() throws Exception {
    CloseableHttpResponse response =
        TestUtils.send(
            "liveness_check",
            "GET",
            HeaderUtils.getHeaders(TenantUtils.getTenantName(), null),
            "",
            "");
    assertEquals(HttpStatus.SC_OK, response.getCode());
  }
}
