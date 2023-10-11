package org.opengroup.osdu.storage.HeaderValidations;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AzureTestUtils;
import org.opengroup.osdu.storage.util.ConfigUtils;

public class TestValidateRequiredHeaders extends ValidateRequiredHeaders {

    private static final AzureTestUtils azureTestUtils = new AzureTestUtils();

    @BeforeClass
    public static void classSetup() throws Exception {
        ValidateRequiredHeaders.classSetup(azureTestUtils.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        ValidateRequiredHeaders.classTearDown(azureTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AzureTestUtils();
        this.configUtils = new ConfigUtils("test.properties");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
        this.configUtils = null;
    }
}
