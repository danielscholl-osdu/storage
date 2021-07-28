package org.opengroup.osdu.storage.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.VersionInfoUtils;

public abstract class GetQueryInfoIntegrationTest extends TestBase {

  protected static final VersionInfoUtils VERSION_INFO_UTILS = new VersionInfoUtils();

  @Test
  public void should_returnInfo() throws Exception {
    ClientResponse response = TestUtils
        .send("info", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(),
            testUtils.getToken()), "", "");
    assertEquals(HttpStatus.SC_OK, response.getStatus());

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
