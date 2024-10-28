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

package org.opengroup.osdu.storage.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class HealthCheckApiIntegrationTest extends TestBase {

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
