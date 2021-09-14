package org.opengroup.osdu.storage.records;

import org.junit.After;
import org.junit.Before;
import org.opengroup.osdu.storage.util.AzureTestUtils;

public class TestLogicalBatchRecordsDelete extends LogicalBatchRecordsDeleteTests {

    private static final AzureTestUtils azureTestUtils = new AzureTestUtils();


    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AzureTestUtils();
        super.setup(azureTestUtils.getToken());
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
        super.tearDown(azureTestUtils.getToken());
    }
}
