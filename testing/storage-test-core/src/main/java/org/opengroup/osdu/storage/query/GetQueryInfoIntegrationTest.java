package org.opengroup.osdu.storage.query;

import java.util.List;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.*;
import org.opengroup.osdu.core.common.model.info.VersionInfo;
import org.opengroup.osdu.core.common.model.info.FeatureFlagStateResolver.FeatureFlagState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public abstract class GetQueryInfoIntegrationTest extends TestBase {

  protected static final VersionInfoUtils VERSION_INFO_UTILS = new VersionInfoUtils();
  
  // Feature flag property constant - matches the value used in FeatureConstants
  private static final String EXPOSE_FEATUREFLAG_ENABLED_PROPERTY = "expose_featureflag.enabled";

  private static final List<String> expectedFeatureFlags = List.of(
      "collaborations-enabled",
      "featureFlag.opa.enabled"
  );

  @Test
  public void should_returnInfo() throws Exception {
    CloseableHttpResponse response = TestUtils
        .send("info", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(),
            testUtils.getToken()), "", "");
    assertEquals(HttpStatus.SC_OK, response.getCode());

    VersionInfo responseObject = VERSION_INFO_UTILS.getVersionInfoFromResponse(response);

    assertNotNull(responseObject.getGroupId());
    assertNotNull(responseObject.getArtifactId());
    assertNotNull(responseObject.getVersion());
    assertNotNull(responseObject.getBuildTime());
    assertNotNull(responseObject.getBranch());
    assertNotNull(responseObject.getCommitId());
    assertNotNull(responseObject.getCommitMessage());

    List<FeatureFlagState> featureFlagStates = responseObject.getFeatureFlagStates();
    
    // Read the actual configuration property value to validate behavior alignment
    // Check system property first, then fall back to environment variable
    String featureFlagExposeEnabledProperty = System.getProperty(EXPOSE_FEATUREFLAG_ENABLED_PROPERTY);
    if (featureFlagExposeEnabledProperty == null) {
        featureFlagExposeEnabledProperty = System.getenv("EXPOSE_FEATUREFLAG_ENABLED");
    }
    boolean isFeatureFlagExposureEnabled = featureFlagExposeEnabledProperty == null || !"false".equalsIgnoreCase(featureFlagExposeEnabledProperty);
    
    if (!isFeatureFlagExposureEnabled)
    {
      assertNull(featureFlagStates);
    }
    else
    {
      assertNotNull(featureFlagStates);
      assertFalse(featureFlagStates.isEmpty());
      for (String ffName : expectedFeatureFlags){
        assertTrue(featureFlagStates.stream().anyMatch(ffState -> ffState.getName().equals(ffName)));    
      }    
    }
  }
}
