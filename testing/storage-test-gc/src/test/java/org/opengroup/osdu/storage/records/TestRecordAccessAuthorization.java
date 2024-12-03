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
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
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
        this.configUtils = new ConfigUtils("test.properties");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
	}

    @Override
    public void should_receiveHttp403_when_userIsNotAuthorizedToUpdateARecord() throws Exception {
      boolean opaIntegrationEnabled = Boolean.parseBoolean(
          System.getProperty("opa.integration.enabled",
              System.getenv("OPA_INTEGRATION_ENABLED")));
      if (!opaIntegrationEnabled) {
        super.should_receiveHttp403_when_userIsNotAuthorizedToUpdateARecord();
      } else {
        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
            testUtils.getNoDataAccessToken());

        CloseableHttpResponse response = TestUtils.send("records", "PUT", headers,
            RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");

        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
        assertEquals(401, json.get("code").getAsInt());
      }
    }
}
