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
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class PurgeRecordsIntegrationTest extends TestBase {

	protected static final long NOW = System.currentTimeMillis();

	protected static final String RECORD_ID = TenantUtils.getTenantName() + ":getrecord:" + NOW;
	protected static final String RECORD_ID1 = TenantUtils.getTenantName() + ":getrecord1:" + NOW;
	protected static final String RECORD_ID2 = TenantUtils.getTenantName() + ":getrecord2:" + NOW;
	protected static final String KIND = TenantUtils.getTenantName() + ":ds:getrecord:1.0." + NOW;
	protected static final String LEGAL_TAG = LegalTagUtils.createRandomName();
	public static final String HTTP_DELETE = "DELETE";

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG, token);
		String jsonInput = RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG);
		String jsonInput1 = RecordUtil.createDefaultJsonRecord(RECORD_ID1, KIND, LEGAL_TAG);
		String jsonInput2 = RecordUtil.createDefaultJsonRecord(RECORD_ID2, KIND, LEGAL_TAG);

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput, "");
		assertEquals(201, response.getCode());
		assertTrue(response.getEntity().getContentType().contains("application/json"));

		for(int i=0; i < 4; i++) {
			// Create 4 record versions - for limit scenario
			CloseableHttpResponse response1 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput1, "");
			assertEquals(201, response1.getCode());
			assertTrue(response1.getEntity().getContentType().contains("application/json"));

			// Create 4 record versions - for versionIds scenario
			CloseableHttpResponse response2 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput2, "");
			assertEquals(201, response2.getCode());
			assertTrue(response2.getEntity().getContentType().contains("application/json"));

		}
	}

	public static void classTearDown(String token) throws Exception {
		TestUtils.send("records/" + RECORD_ID1, HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID2, HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");

		LegalTagUtils.delete(LEGAL_TAG, token);
	}

	@Test
	public void should_ReturnHttp204_when_purgingRecordSuccessfully() throws Exception {
		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());

		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getCode());
	}

	@Test
	public void shouldReturnHttp204_whenPurgeRecordVersions_byLimit_isSuccess() throws Exception {
		String queryParams= "?limit=2";
		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID1 + "/versions", HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", queryParams);
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());

		CloseableHttpResponse getVersionResponse = TestUtils.send("records/versions/" + RECORD_ID1, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		JsonObject json = JsonParser.parseString(EntityUtils.toString(getVersionResponse.getEntity())).getAsJsonObject();
		JsonArray versions = json.get("versions").getAsJsonArray();
		// Record version : 4, limit: 2, Deleted version paths: 2
		assertEquals(HttpStatus.SC_OK, getVersionResponse.getCode());
		assertEquals(RECORD_ID1, json.get("recordId").getAsString());
		assertEquals(2, versions.size());
	}

	@Test
	public void shouldReturnHttp204_whenPurgeRecordVersions_byVersionIds_isSuccess() throws Exception {
		CloseableHttpResponse getVersionResponse = TestUtils.send("records/versions/" + RECORD_ID2, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		JsonObject json = JsonParser.parseString(EntityUtils.toString(getVersionResponse.getEntity())).getAsJsonObject();
		JsonArray versions = json.get("versions").getAsJsonArray();
		String versionIds = versions.get(0).getAsString() + "," + versions.get(1).getAsString();

		String queryParams = "?limit=2&versionIds="+versionIds;
		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID2 + "/versions", HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", queryParams);
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());

		CloseableHttpResponse getVersionResponse1 = TestUtils.send("records/versions/" + RECORD_ID2, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		JsonObject json1 = JsonParser.parseString(EntityUtils.toString(getVersionResponse1.getEntity())).getAsJsonObject();
		JsonArray versions1 = json1.get("versions").getAsJsonArray();
		// Record version : 4, versionIds to delete: 2, Deleted version paths: 2
		assertEquals(HttpStatus.SC_OK, getVersionResponse1.getCode());
		assertEquals(RECORD_ID2, json1.get("recordId").getAsString());
		assertEquals(2, versions1.size());
	}
}
