package org.opengroup.osdu.storage.records;

import org.junit.After;
import org.junit.Before;
import org.opengroup.osdu.storage.util.AzureTestUtils;

public class TestParentRecordValidation extends ParentRecordValidationTest {

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AzureTestUtils();
        super.setup();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        this.testUtils = null;
    }
}
