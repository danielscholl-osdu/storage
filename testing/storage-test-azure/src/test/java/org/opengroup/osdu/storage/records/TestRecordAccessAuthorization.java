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
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.*;
import org.opengroup.osdu.storage.util.AzureTestUtils;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestUtils;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestRecordAccessAuthorization extends RecordAccessAuthorizationTests {

    private static final AzureTestUtils azureTestUtils = new AzureTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        RecordAccessAuthorizationTests.classSetup(azureTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        RecordAccessAuthorizationTests.classTearDown(azureTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AzureTestUtils();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
	}

	@Override
    @Test
    public void should_receiveHttp403_when_userIsNotAuthorizedToPurgeRecord() throws Exception {
        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
                testUtils.getNoDataAccessToken());

        CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, "DELETE", headers, "", "");

        assertEquals(HttpStatus.SC_FORBIDDEN, response.getCode());
        JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
        assertEquals(403, json.get("code").getAsInt());
        assertEquals("Access denied", json.get("reason").getAsString());
        assertEquals("The user is not authorized to perform this action", json.get("message").getAsString());
    }

}
