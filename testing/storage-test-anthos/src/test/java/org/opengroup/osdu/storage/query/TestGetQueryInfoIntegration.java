package org.opengroup.osdu.storage.query;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AnthosTestUtils;

public class TestGetQueryInfoIntegration extends GetQueryInfoIntegrationTest {

    private static final AnthosTestUtils ANTHOS_TEST_UTILS = new AnthosTestUtils();

    @BeforeClass
    public static void classSetup() throws Exception {
        GetQueryRecordsIntegrationTest.classSetup(ANTHOS_TEST_UTILS.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        GetQueryRecordsIntegrationTest.classTearDown(ANTHOS_TEST_UTILS.getToken());
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
