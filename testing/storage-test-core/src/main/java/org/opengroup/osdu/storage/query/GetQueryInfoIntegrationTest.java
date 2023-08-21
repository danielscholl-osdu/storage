package org.opengroup.osdu.storage.query;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class GetQueryInfoIntegrationTest extends TestBase {

  protected static final VersionInfoUtils VERSION_INFO_UTILS = new VersionInfoUtils();

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
