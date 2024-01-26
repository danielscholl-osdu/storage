package org.opengroup.osdu.storage.records;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AWSTestUtils;
import org.opengroup.osdu.storage.util.ConfigUtils;

public class TestCollaborationPurgeRecords extends CollaborationRecordsPurgeTest {
    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AWSTestUtils();
        this.configUtils = new ConfigUtils("test.properties");
        super.setup();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        this.testUtils = null;
        this.configUtils = null;
    }
}
