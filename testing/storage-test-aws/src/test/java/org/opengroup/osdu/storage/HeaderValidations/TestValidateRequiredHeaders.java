package org.opengroup.osdu.storage.HeaderValidations;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AWSTestUtils;

public class TestValidateRequiredHeaders extends ValidateRequiredHeaders {
    private static final AWSTestUtils awsTestUtils = new AWSTestUtils();

    @BeforeClass
    public static void classSetup() throws Exception {
        ValidateRequiredHeaders.classSetup(awsTestUtils.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        ValidateRequiredHeaders.classTearDown(awsTestUtils.getToken());
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
