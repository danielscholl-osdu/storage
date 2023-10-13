package org.opengroup.osdu.storage.HeaderValidations;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AnthosTestUtils;

public class TestValidateRequiredHeaders extends ValidateRequiredHeaders {
    private static final AnthosTestUtils ANTHOS_TEST_UTILS = new AnthosTestUtils();

    @BeforeClass
    public static void classSetup() throws Exception {
        ValidateRequiredHeaders.classSetup(ANTHOS_TEST_UTILS.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        ValidateRequiredHeaders.classTearDown(ANTHOS_TEST_UTILS.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AnthosTestUtils();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
    }
}
