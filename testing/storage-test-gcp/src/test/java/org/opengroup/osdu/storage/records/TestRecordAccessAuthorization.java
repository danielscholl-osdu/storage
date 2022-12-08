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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.GCPTestUtils;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestUtils;

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
    public void should_receiveHttp403_when_userIsNotAuthorizedToUpdateARecord() throws Exception {
        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
            testUtils.getNoDataAccessToken());

        ClientResponse response = TestUtils.send("records", "PUT", headers,
            RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");

        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatus());
        JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
        assertEquals(401, json.get("code").getAsInt());
        assertEquals("Error from compliance service", json.get("reason").getAsString());
        assertEquals("Legal response 401 {\"code\":401,\"reason\":\"Unauthorized\",\"message\":\"The user is not authorized to perform this action\"}", json.get("message").getAsString());
    }
}
