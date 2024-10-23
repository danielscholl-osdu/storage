package org.opengroup.osdu.storage.records;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

public final class RecordWithEntV2OnlyAclTest extends TestBase {

    private static final long NOW = System.currentTimeMillis();
    private static final String LEGAL_TAG = LegalTagUtils.createRandomName();
    private static final String KIND = TenantUtils.getTenantName() + ":test:inttest:1.1." + NOW;
    private static final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:" + NOW;

    @BeforeEach
    public void setup() throws Exception {
        this.testUtils = new TokenTestUtils();
        LegalTagUtils.create(LEGAL_TAG, testUtils.getToken());
    }

    @AfterEach
    public void tearDown() throws Exception {
        LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());
        TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        this.testUtils = null;
    }

    @Test
    public void should_allow_recordWithAclThatExistsIOnlyInEntV2() throws Exception{
        //create record
        CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createJsonRecordWithEntV2OnlyAcl(RECORD_ID, KIND, LEGAL_TAG, RECORD_ID), "");
        assertEquals(HttpStatus.SC_CREATED, response.getCode());
    }

}
