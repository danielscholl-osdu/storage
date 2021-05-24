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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;
import org.junit.*;
import org.opengroup.osdu.storage.util.*;

import static org.junit.Assert.assertEquals;

public class TestRecordsApiAcceptance extends RecordsApiAcceptanceTests {

    private static final AzureTestUtils azureTestUtils = new AzureTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        RecordsApiAcceptanceTests.classSetup(azureTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        RecordsApiAcceptanceTests.classTearDown(azureTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AzureTestUtils();
        this.configUtils = new ConfigUtils("test.properties");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
	}

    @Test
    public void should_createNewRecords_whenTheyHaveSameFirst100Characters() throws Exception {
        final String RECORDID_1 = TenantUtils.getTenantName() + ":marker:hij-osdu-dev-sis-internal-hq-techlog--A52C-4031-99D1---124CBB92-8C80-4668-BB48-1329C5FE241C--1";
        final String RECORDID_2 = TenantUtils.getTenantName() + ":marker:hij-osdu-dev-sis-internal-hq-techlog--A52C-4031-99D1---124CBB92-8C80-4668-BB48-1329C5FE241C--9";

        String jsonInput = "[" + singleEntityBody(RECORDID_1, "TestCreation", KIND, LEGAL_TAG) + "," + singleEntityBody(RECORDID_2, "TestCreation", KIND, LEGAL_TAG) + "]";
        ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");

        assertEquals(201, response.getStatus());

        response = TestUtils.send("records/" + RECORDID_1, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        String responseString = TestUtils.getResult(response, 200, String.class);
        JsonObject responseJson = new JsonParser().parse(responseString).getAsJsonObject();

        assertEquals(200, response.getStatus());
        assertEquals(RECORDID_1, responseJson.get("id").getAsString());

        response = TestUtils.send("records/" + RECORDID_2, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        responseString = TestUtils.getResult(response, 200, String.class);
        responseJson = new JsonParser().parse(responseString).getAsJsonObject();
        assertEquals(200, response.getStatus());
        assertEquals(RECORDID_2, responseJson.get("id").getAsString());
    }
}