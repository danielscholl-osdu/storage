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

package org.opengroup.osdu.storage.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;
import org.opengroup.osdu.storage.util.VersionInfoUtils;


public final class GetQueryInfoIntegrationTest extends TestBase {

  private static final VersionInfoUtils VERSION_INFO_UTILS = new VersionInfoUtils();
  private static final TokenTestUtils TOKEN_TEST_UTILS = new TokenTestUtils();

  @BeforeAll
  public static void classSetup() throws Exception {
    GetQueryRecordsIntegrationTest.classSetup(TOKEN_TEST_UTILS.getToken());
  }

  @AfterAll
  public static void classTearDown() throws Exception {
    GetQueryRecordsIntegrationTest.classTearDown(TOKEN_TEST_UTILS.getToken());
  }

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
  public void should_returnInfo() throws Exception {
    CloseableHttpResponse response = TestUtils
        .send("info", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(),
            testUtils.getToken()), "", "");
    assertEquals(HttpStatus.SC_OK, response.getCode());

    VersionInfoUtils.VersionInfo responseObject = VERSION_INFO_UTILS.getVersionInfoFromResponse(response);

    assertNotNull(responseObject.groupId);
    assertNotNull(responseObject.artifactId);
    assertNotNull(responseObject.version);
    assertNotNull(responseObject.buildTime);
    assertNotNull(responseObject.branch);
    assertNotNull(responseObject.commitId);
    assertNotNull(responseObject.commitMessage);
  }
}
