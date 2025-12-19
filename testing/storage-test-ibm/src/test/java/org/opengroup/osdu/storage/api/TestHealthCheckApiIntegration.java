package org.opengroup.osdu.storage.api;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestUtils;

import static org.junit.Assert.assertEquals;

public class TestHealthCheckApiIntegration extends HealthCheckApiIntegrationTest {
  @Before
  @Override
  public void setup() throws Exception {}


  @Override
  @Test
  public void should_returnOk() throws Exception {
  }

  @After
  @Override
  public void tearDown() throws Exception {}
}
