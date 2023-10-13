package org.opengroup.osdu.storage.HeaderValidations;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.GCPTestUtils;

public class TestValidateRequiredHeaders extends ValidateRequiredHeaders {
    private static final GCPTestUtils gcpTestUtils = new GCPTestUtils();

    @BeforeClass
    public static void classSetup() throws Exception {
        ValidateRequiredHeaders.classSetup(gcpTestUtils.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        ValidateRequiredHeaders.classTearDown(gcpTestUtils.getToken());
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
