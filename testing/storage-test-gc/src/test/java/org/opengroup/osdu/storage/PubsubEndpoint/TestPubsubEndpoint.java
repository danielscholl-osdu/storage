// Copyright 2017-2019, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.PubsubEndpoint;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.*;
import org.opengroup.osdu.storage.util.*;

import java.util.ArrayList;
import java.util.List;

public class TestPubsubEndpoint extends PubsubEndpointTest {

    private static final GCPTestUtils gcpTestUtils = new GCPTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        PubsubEndpointTest.classSetup(gcpTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        PubsubEndpointTest.classTearDown(gcpTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new GCPTestUtils();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
	}

    @Override
    public void should_deleteIncompliantLegaltagAndInvalidateRecordsAndNotIngestAgain_whenIncompliantMessageSentToEndpoint() throws Exception {
        LegalTagUtils.delete(LEGAL_TAG_1, testUtils.getToken());
        // wait until cache of opa is rebuilt
        Thread.sleep(100000);

        List<String> legalTagNames = new ArrayList<>();
        legalTagNames.add(LEGAL_TAG_1);
        legalTagNames.add(LEGAL_TAG_2);
        CloseableHttpResponse responseRecordQuery =
            TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
                "");
        Assert.assertEquals(HttpStatus.SC_NOT_FOUND, responseRecordQuery.getCode());

        long now = System.currentTimeMillis();
        long later = now + 2000L;
        String recordIdTemp1 = TenantUtils.getTenantName() + ":endtoend:1.1." + now;
        String kindTemp = TenantUtils.getTenantName() + ":test:endtoend:1.1." + now;
        String recordTemp1 = RecordUtil.createDefaultJsonRecord(recordIdTemp1, kindTemp, LEGAL_TAG_1);
        String recordIdTemp2 = TenantUtils.getTenantName() + ":endtoend:1.1." + later;
        String recordTemp2 = RecordUtil.createDefaultJsonRecord(recordIdTemp2, kindTemp, LEGAL_TAG_2);

        CloseableHttpResponse responseInvalid =
            TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), recordTemp1, "");
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, responseInvalid.getCode());
        Assert.assertEquals("Invalid legal tags", this.getResponseReasonFromRecordIngestResponse(responseInvalid));
        CloseableHttpResponse responseValid3 =
            TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), recordTemp2, "");
        Assert.assertEquals(HttpStatus.SC_CREATED, responseValid3.getCode());
        TestUtils.send("records/" + recordIdTemp2, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    }
}
