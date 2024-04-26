package org.opengroup.osdu.storage.records;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AWSTestUtils;

public class TestCopyRecordReferences extends CopyRecordReferencesTest{

  private static final AWSTestUtils awsTestUtils = new AWSTestUtils();

  @BeforeClass
  public static void classSetup() throws Exception {
    CopyRecordReferencesTest.classSetup(awsTestUtils.getToken());
  }

  @AfterClass
  public static void classTearDown() throws Exception {
    CopyRecordReferencesTest.classTearDown(awsTestUtils.getToken());
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
