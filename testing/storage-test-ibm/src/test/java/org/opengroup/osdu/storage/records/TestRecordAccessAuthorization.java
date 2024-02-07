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
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.*;
import org.opengroup.osdu.storage.util.*;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;

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
        Map<String, String> headers = HeaderUtils.getHeaders("nonexistenttenant",
            testUtils.getToken());

        CloseableHttpResponse response = TestUtils.send("records", "PUT", headers,
            RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");

        assertEquals(HttpStatus.SC_FORBIDDEN, response.getCode());
        JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
        assertEquals(403, json.get("code").getAsInt());
        assertEquals("Access denied", json.get("reason").getAsString());
        assertEquals("Invalid data partition id", json.get("message").getAsString());
    }
    @Test
    @Override
    public void should_receiveHttp403_when_userIsNotAuthorizedToGetLatestVersionOfARecord() throws Exception {
        Map<String, String> headers = HeaderUtils.getHeaders("nonexistenttenant", this.testUtils.getToken());
        CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, "GET", headers, "", "");
        this.assertNotAuthorized(response);
    }

    @Override
    @Test
    public void should_receiveHttp403_when_userIsNotAuthorizedToDeleteRecord() throws Exception {
        Map<String, String> headers = HeaderUtils.getHeaders("nonexistenttenant",
                testUtils.getToken());

        CloseableHttpResponse response = TestUtils.send("records/", "POST", headers, "{'anything':'anything'}",
                RECORD_ID + ":delete");

        this.assertNotAuthorized(response);
    }

    @Override
    @Test
    public void should_receiveHttp403_when_userIsNotAuthorizedToGetSpecificVersionOfARecord() throws Exception {
        Map<String, String> withDataAccessHeader = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
                testUtils.getToken());

        CloseableHttpResponse response = TestUtils.send("records/versions/" + RECORD_ID, "GET", withDataAccessHeader, "", "");
        JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
        String version = json.get("versions").getAsJsonArray().get(0).toString();

        Map<String, String> withoutDataAccessHeader = HeaderUtils.getHeaders("nonexistenttenant",
                testUtils.getToken());

        response = TestUtils.send("records/" + RECORD_ID + "/" + version, "GET", withoutDataAccessHeader, "", "");

        this.assertNotAuthorized(response);
    }

    @Override
    @Test
    public void should_receiveHttp403_when_userIsNotAuthorizedToListVersionsOfARecord() throws Exception {
        Map<String, String> headers = HeaderUtils.getHeaders("nonexistenttenant",
                testUtils.getToken());

        CloseableHttpResponse response = TestUtils.send("records/versions/" + RECORD_ID, "GET", headers, "", "");

        this.assertNotAuthorized(response);
    }

    @Test
    public void should_receiveHttp403_when_userIsNotAuthorizedToPurgeRecord() throws Exception {
        Map<String, String> headers = HeaderUtils.getHeaders("nonexistenttenant", this.testUtils.getToken());
        CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, "DELETE", headers, "", "");
        Assert.assertEquals(403, response.getCode());
        JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
        Assert.assertEquals(403, json.get("code").getAsInt());
        Assert.assertEquals("Access denied", json.get("reason").getAsString());
    }

    @Override
    public void should_NoneRecords_when_fetchingMultipleRecords_and_notAuthorizedToRecords()
            throws Exception {

        // Creates a new record
        String newRecordId = TenantUtils.getTenantName() + ":no:2.2." + NOW;

        Map<String, String> headersWithValidAccessToken = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
                testUtils.getToken());

        CloseableHttpResponse response = TestUtils.send("records", "PUT", headersWithValidAccessToken,
                RecordUtil.createDefaultJsonRecord(newRecordId, KIND, LEGAL_TAG), "");

        assertEquals(HttpStatus.SC_CREATED, response.getCode());

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
        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());


        TestUtils.send("records/" + newRecordId, "DELETE", headersWithNoDataAccessToken, "", "");
    }

    @Override
    protected void assertNotAuthorized(CloseableHttpResponse response) {
        assertEquals(HttpStatus.SC_FORBIDDEN, response.getCode());
        JsonObject json = null;
        try {
            json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        assertEquals(403, json.get("code").getAsInt());
        assertEquals("Access denied", json.get("reason").getAsString());
        assertEquals("Invalid data partition id", json.get("message").getAsString());
    }

}
