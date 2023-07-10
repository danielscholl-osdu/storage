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

import static org.junit.Assert.assertEquals;

import java.util.Map;

import com.google.gson.JsonArray;
import org.apache.http.HttpStatus;
import org.junit.*;
import org.opengroup.osdu.storage.util.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;

public class TestRecordAccessAuthorization extends RecordAccessAuthorizationTests {

    private static final IBMTestUtils ibmTestUtils = new IBMTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        RecordAccessAuthorizationTests.classSetup(ibmTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        RecordAccessAuthorizationTests.classTearDown(ibmTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new IBMTestUtils();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
	}
    
    @Override
    public void should_receiveHttp403_when_userIsNotAuthorizedToUpdateARecord() throws Exception {
        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
            testUtils.getNoDataAccessToken());

        ClientResponse response = TestUtils.send("records", "PUT", headers,
            RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");

        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatus());
        JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
        assertEquals(401, json.get("code").getAsInt());
        assertEquals("Access denied", json.get("reason").getAsString());
        assertEquals("The user is not authorized to perform this action", json.get("message").getAsString());
    }
    @Test
    @Override
    public void should_receiveHttp403_when_userIsNotAuthorizedToGetLatestVersionOfARecord() throws Exception {
        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), this.testUtils.getNoDataAccessToken());
        ClientResponse response = TestUtils.send("records/" + RECORD_ID, "GET", headers, "", "");
        this.assertNotAuthorized(response);
    }
    protected void assertNotAuthorized(ClientResponse response) {
        Assert.assertEquals(401L, (long)response.getStatus());
        JsonObject json = (new JsonParser()).parse((String)response.getEntity(String.class)).getAsJsonObject();
        Assert.assertEquals(401L, (long)json.get("code").getAsInt());
        Assert.assertEquals("Access denied", json.get("reason").getAsString());
        Assert.assertEquals("The user is not authorized to perform this action", json.get("message").getAsString());
    }
    @Test
    public void should_receiveHttp403_when_userIsNotAuthorizedToPurgeRecord() throws Exception {
        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), this.testUtils.getNoDataAccessToken());
        ClientResponse response = TestUtils.send("records/" + RECORD_ID, "DELETE", headers, "", "");
        Assert.assertEquals(401L, (long)response.getStatus());
        JsonObject json = (new JsonParser()).parse((String)response.getEntity(String.class)).getAsJsonObject();
        Assert.assertEquals(401L, (long)json.get("code").getAsInt());
        Assert.assertEquals("Access denied", json.get("reason").getAsString());
    }

    @Override
    public void should_NoneRecords_when_fetchingMultipleRecords_and_notAuthorizedToRecords()
            throws Exception {

        // Creates a new record
        String newRecordId = TenantUtils.getTenantName() + ":no:2.2." + NOW;

        Map<String, String> headersWithValidAccessToken = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
                testUtils.getToken());

        ClientResponse response = TestUtils.send("records", "PUT", headersWithValidAccessToken,
                RecordUtil.createDefaultJsonRecord(newRecordId, KIND, LEGAL_TAG), "");

        assertEquals(HttpStatus.SC_CREATED, response.getStatus());

        // Query for original record (no access) and recently created record (with
        // access)
        Map<String, String> headersWithNoDataAccessToken = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
                testUtils.getNoDataAccessToken());

        JsonArray records = new JsonArray();
        records.add(RECORD_ID);
        records.add(newRecordId);

        JsonObject body = new JsonObject();
        body.add("records", records);

        response = TestUtils.send("query/records", "POST", headersWithNoDataAccessToken, body.toString(), "");
        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatus());


        TestUtils.send("records/" + newRecordId, "DELETE", headersWithNoDataAccessToken, "", "");
    }

}
