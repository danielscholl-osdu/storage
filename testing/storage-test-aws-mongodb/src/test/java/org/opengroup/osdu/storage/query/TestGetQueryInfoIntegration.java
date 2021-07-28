package org.opengroup.osdu.storage.query;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AWSTestUtils;

public class TestGetQueryInfoIntegration extends GetQueryInfoIntegrationTest {

  private static final AWSTestUtils awsTestUtils = new AWSTestUtils();

  @BeforeClass
  public static void classSetup() throws Exception {
    GetQueryRecordsIntegrationTest.classSetup(awsTestUtils.getToken());
  }

  @AfterClass
  public static void classTearDown() throws Exception {
    GetQueryRecordsIntegrationTest.classTearDown(awsTestUtils.getToken());
  }

  @Before
  @Override
  public void setup() throws Exception {
    this.testUtils = new AWSTestUtils();
  }

  @After
  @Override
  public void tearDown() throws Exception {
    this.testUtils = null;
  }
}
