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
import org.opengroup.osdu.storage.util.TokenTestUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
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
