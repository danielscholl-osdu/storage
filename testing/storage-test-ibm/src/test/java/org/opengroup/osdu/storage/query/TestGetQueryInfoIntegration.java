package org.opengroup.osdu.storage.query;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.IBMTestUtils;

public class TestGetQueryInfoIntegration extends GetQueryInfoIntegrationTest {

  private static final IBMTestUtils ibmTestUtils = new IBMTestUtils();

  @BeforeClass
  public static void classSetup() throws Exception {
    GetQueryRecordsIntegrationTest.classSetup(ibmTestUtils.getToken());
  }

  @AfterClass
  public static void classTearDown() throws Exception {
    GetQueryRecordsIntegrationTest.classTearDown(ibmTestUtils.getToken());
  }

  @Before
  @Override
  public void setup() throws Exception {
    this.testUtils = new IBMTestUtils();
  }

  @After
  @Override
  public void tearDown() throws Exception {
    this.testUtils = null;
  }
}
