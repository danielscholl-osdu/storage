package org.opengroup.osdu.storage.HeaderValidations;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.legal.PopulateLegalInfoFromParentRecordsTests;
import org.opengroup.osdu.storage.util.IBMTestUtils;

public class TestValidateRequiredHeaders extends ValidateRequiredHeaders {
    private static final IBMTestUtils ibmTestUtils = new IBMTestUtils();

    @BeforeClass
    public static void classSetup() throws Exception {
        PopulateLegalInfoFromParentRecordsTests.classSetup(ibmTestUtils.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        PopulateLegalInfoFromParentRecordsTests.classTearDown(ibmTestUtils.getToken());
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
