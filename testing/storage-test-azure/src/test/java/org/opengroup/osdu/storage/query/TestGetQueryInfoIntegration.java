package org.opengroup.osdu.storage.query;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AzureTestUtils;

public class TestGetQueryInfoIntegration extends GetQueryInfoIntegrationTest {

  private static final AzureTestUtils azureTestUtils = new AzureTestUtils();

  @Before
  @Override
  public void setup() throws Exception {
    this.testUtils = new AzureTestUtils();
  }

  @After
  @Override
  public void tearDown() throws Exception {
    this.testUtils = null;
  }
}
