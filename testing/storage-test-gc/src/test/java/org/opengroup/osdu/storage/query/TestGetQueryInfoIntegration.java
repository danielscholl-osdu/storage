package org.opengroup.osdu.storage.query;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.GCPTestUtils;

public class TestGetQueryInfoIntegration extends GetQueryInfoIntegrationTest {

  private static final GCPTestUtils gcpTestUtils = new GCPTestUtils();

  @BeforeClass
  public static void classSetup() throws Exception {
    GetQueryRecordsIntegrationTest.classSetup(gcpTestUtils.getToken());
  }

  @AfterClass
  public static void classTearDown() throws Exception {
    GetQueryRecordsIntegrationTest.classTearDown(gcpTestUtils.getToken());
  }

  @Before
  @Override
  public void setup() throws Exception {
    this.testUtils = new GCPTestUtils();
  }

  @After
  @Override
  public void tearDown() throws Exception {
    this.testUtils = null;
  }
}
