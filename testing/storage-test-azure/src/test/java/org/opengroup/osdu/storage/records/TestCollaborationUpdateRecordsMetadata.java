package org.opengroup.osdu.storage.records;

import org.junit.After;
import org.junit.Before;
import org.opengroup.osdu.storage.util.AzureTestUtils;
import org.opengroup.osdu.storage.util.ConfigUtils;

public class TestCollaborationUpdateRecordsMetadata extends CollaborationUpdateRecordsMetadataTest {
    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AzureTestUtils();
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
