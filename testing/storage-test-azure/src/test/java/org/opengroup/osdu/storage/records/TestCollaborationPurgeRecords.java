package org.opengroup.osdu.storage.records;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AzureTestUtils;

public class TestCollaborationPurgeRecords extends CollaborationRecordsPurgeTest {
    private static final AzureTestUtils azureTestUtils = new AzureTestUtils();

    @BeforeClass
    public static void classSetup() throws Exception {
        CollaborationRecordsPurgeTest.classSetup(azureTestUtils.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        CollaborationRecordsPurgeTest.classTearDown(azureTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AzureTestUtils();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
    }
}
