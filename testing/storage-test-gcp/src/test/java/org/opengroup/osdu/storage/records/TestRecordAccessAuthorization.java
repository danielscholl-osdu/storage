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

package org.opengroup.osdu.storage.records;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;
import org.junit.*;
import org.opengroup.osdu.storage.util.*;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestRecordAccessAuthorization extends RecordAccessAuthorizationTests {

    private static final GCPTestUtils gcpTestUtils = new GCPTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        RecordAccessAuthorizationTests.classSetup(gcpTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        RecordAccessAuthorizationTests.classTearDown(gcpTestUtils.getToken());
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
    @Test
    public void should_NoneRecords_when_fetchingMultipleRecords_and_notAuthorizedToRecords()
            throws Exception {

        // Creates a new record
        String newRecordId = TenantUtils.getTenantName() + ":no:2.2." + NOW;

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
                testUtils.getToken());

        ClientResponse response = TestUtils.send("records", "PUT", headers,
                RecordUtil.createDefaultJsonRecord(newRecordId, KIND, LEGAL_TAG), "");

        assertEquals(HttpStatus.SC_CREATED, response.getStatus());

        // Query for original record (no access) and recently created record (with
        // access)
        JsonArray records = new JsonArray();
        records.add(RECORD_ID);
        records.add(newRecordId);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> noDataAccessHeaders = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
                testUtils.getNoDataAccessToken());

        response = TestUtils.send("query/records", "POST", noDataAccessHeaders, body.toString(), "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.RecordsMock responseObject = new DummyRecordsHelper().getRecordsMockFromResponse(response);

        assertEquals(0, responseObject.records.length);
        assertEquals(0, responseObject.invalidRecords.length);
        assertEquals(0, responseObject.retryRecords.length);

        TestUtils.send("records/" + newRecordId, "DELETE", headers, "", "");
    }
}