package org.opengroup.osdu.storage.records;

import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengroup.osdu.storage.util.*;

import static org.junit.Assert.assertEquals;

public abstract class RecordWithEntV2OnlyAclTest extends TestBase {

    protected static final long NOW = System.currentTimeMillis();
    protected static final String LEGAL_TAG = LegalTagUtils.createRandomName();
    protected static final String KIND = TenantUtils.getTenantName() + ":test:inttest:1.1." + NOW;
    protected static final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:" + NOW;

    @Before
    public void setup() throws Exception {
        LegalTagUtils.create(LEGAL_TAG, testUtils.getToken());
    }

    @After
    public void tearDown() throws Exception {
        LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());

        TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    }

    @Test
    public void should_allow_recordWithAclThatExistsIOnlyInEntV2() throws Exception{
        //create record
        ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createJsonRecordWithEntV2OnlyAcl(RECORD_ID, KIND, LEGAL_TAG, RECORD_ID), "");
        assertEquals(HttpStatus.SC_CREATED, response.getStatus());
    }

}
