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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class PurgeRecordsIntegrationTest extends TestBase {

	private static final long NOW = System.currentTimeMillis();
	private static final String RECORD_ID = TenantUtils.getTenantName() + ":getrecord:" + NOW;
	private static final String RECORD_ID1 = TenantUtils.getTenantName() + ":getrecord1:" + NOW;
	private static final String RECORD_ID2 = TenantUtils.getTenantName() + ":getrecord2:" + NOW;
	private static final String RECORD_ID3 = TenantUtils.getTenantName() + ":getrecord3:" + NOW;
	private static final String RECORD_ID4 = TenantUtils.getTenantName() + ":getrecord4:" + NOW;
	private static final String KIND = TenantUtils.getTenantName() + ":ds:getrecord:1.0." + NOW;
	private static final String LEGAL_TAG = LegalTagUtils.createRandomName();
	public static final String HTTP_DELETE = "DELETE";

	private static final TokenTestUtils TOKEN_TEST_UTILS = new TokenTestUtils();

	@BeforeAll
	public static void classSetup() throws Exception {
		PurgeRecordsIntegrationTest.classSetup(TOKEN_TEST_UTILS.getToken());
	}

	@AfterAll
	public static void classTearDown() throws Exception {
		PurgeRecordsIntegrationTest.classTearDown(TOKEN_TEST_UTILS.getToken());
	}

	@BeforeEach
	@Override
	public void setup() throws Exception {
		this.testUtils = new TokenTestUtils();
	}

	@AfterEach
	@Override
	public void tearDown() throws Exception {
		this.testUtils = null;
	}

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG, token);
		String jsonInput = RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG);
		String jsonInput1 = RecordUtil.createDefaultJsonRecord(RECORD_ID1, KIND, LEGAL_TAG);
		String jsonInput2 = RecordUtil.createDefaultJsonRecord(RECORD_ID2, KIND, LEGAL_TAG);
		String jsonInput3 = RecordUtil.createDefaultJsonRecord(RECORD_ID3, KIND, LEGAL_TAG);
		String jsonInput4 = RecordUtil.createDefaultJsonRecord(RECORD_ID4, KIND, LEGAL_TAG);

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput, "");
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
		assertTrue(response.getEntity().getContentType().contains("application/json"));

		for(int i=0; i < 4; i++) {
			// Create 4 record versions - for limit scenario
			CloseableHttpResponse response1 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput1, "");
			assertEquals(HttpStatus.SC_CREATED, response1.getCode());
			assertTrue(response1.getEntity().getContentType().contains("application/json"));

			// Create 4 record versions - for versionIds scenario
			CloseableHttpResponse response2 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput2, "");
			assertEquals(HttpStatus.SC_CREATED, response2.getCode());
			assertTrue(response2.getEntity().getContentType().contains("application/json"));

			// Create 4 record versions - for fromVersion scenario
			CloseableHttpResponse response3 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput3, "");
			assertEquals(HttpStatus.SC_CREATED, response3.getCode());
			assertTrue(response3.getEntity().getContentType().contains("application/json"));

			// Create 4 record versions - for bad requests scenario
			CloseableHttpResponse response4 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput4, "");
			assertEquals(HttpStatus.SC_CREATED, response4.getCode());
			assertTrue(response4.getEntity().getContentType().contains("application/json"));

		}
	}

	public static void classTearDown(String token) throws Exception {
		TestUtils.send("records/" + RECORD_ID1, HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID2, HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID3, HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID4, HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");

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

	@Test
	public void shouldReturnHttp204_whenPurgeRecordVersions_byFromVersion_isSuccess() throws Exception {
		CloseableHttpResponse getVersionResponse = TestUtils.send("records/versions/" + RECORD_ID3, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		JsonObject json = JsonParser.parseString(EntityUtils.toString(getVersionResponse.getEntity())).getAsJsonObject();
		JsonArray versions = json.get("versions").getAsJsonArray();
		String fromVersion =  versions.get(1).getAsString();

		String queryParams = "?from="+fromVersion;
		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID3 + "/versions", HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", queryParams);
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());

		CloseableHttpResponse getVersionResponse1 = TestUtils.send("records/versions/" + RECORD_ID3, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		JsonObject json1 = JsonParser.parseString(EntityUtils.toString(getVersionResponse1.getEntity())).getAsJsonObject();
		JsonArray versions1 = json1.get("versions").getAsJsonArray();
		// Record version : 4, versionIds to delete: 2, Deleted version paths: 2
		assertEquals(HttpStatus.SC_OK, getVersionResponse1.getCode());
		assertEquals(RECORD_ID3, json1.get("recordId").getAsString());
		assertEquals(2, versions1.size());
	}

	@Test
	public void shouldReturnHttp400BadRequest_whenPurgeRecordVersions_forInvalidVersionIds() throws Exception {
		Long versionId1 =  404L;
		Long versionId2 =  405L;
		String invalidVersionIds = versionId1+","+versionId2;
		String queryParams= "?versionIds="+invalidVersionIds;

		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID4 + "/versions", HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", queryParams);
		JsonObject jsonObject = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		String errorMessage = String.format("Invalid Version Ids. The versionIds contains non existing version(s) '%s'", invalidVersionIds);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
		assertEquals(HttpStatus.SC_BAD_REQUEST, jsonObject.get("code").getAsInt());
		assertEquals(errorMessage, jsonObject.get("message").getAsString());
	}

	@Test
	public void shouldReturnHttp400BadRequest_whenPurgeRecordVersions_forLimitExceedsRecordVersions() throws Exception {
		int totalVersions = 4;
		int limitValue = 5;
		String queryParams= "?limit="+limitValue;

		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID4 + "/versions", HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", queryParams);
		JsonObject jsonObject = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		String errorMessage = String.format("The record '%s' version count (excluding latest version) is : %d , which is less than limit value : %d ", RECORD_ID4, totalVersions - 1, limitValue);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
		assertEquals(HttpStatus.SC_BAD_REQUEST, jsonObject.get("code").getAsInt());
		assertEquals("Invalid limit.", jsonObject.get("reason").getAsString());
		assertEquals(errorMessage, jsonObject.get("message").getAsString());
	}

	@Test
	public void shouldReturnHttp400BadRequest_whenPurgeRecordVersions_forInvalidFromVersion() throws Exception {
		Long fromVersion =  404L;
		String queryParams= "?from="+fromVersion;

		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID4 + "/versions", HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", queryParams);
		JsonObject jsonObject = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		String errorMessage = String.format("Invalid 'from' version. The record version does not contains specified from version '%d'", fromVersion);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
		assertEquals(HttpStatus.SC_BAD_REQUEST, jsonObject.get("code").getAsInt());
		assertEquals("Invalid 'from' version.", jsonObject.get("reason").getAsString());
		assertEquals(errorMessage, jsonObject.get("message").getAsString());
	}

	@Test
	public void shouldReturnHttp400BadRequest_whenPurgeRecordVersions_forInvalidLimitAndValidFromVersion() throws Exception {

		CloseableHttpResponse getVersionResponse = TestUtils.send("records/versions/" + RECORD_ID4, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		JsonObject json = JsonParser.parseString(EntityUtils.toString(getVersionResponse.getEntity())).getAsJsonObject();
		JsonArray versions = json.get("versions").getAsJsonArray();
		Long fromVersion =  versions.get(1).getAsLong();

		int limitValue = 3;
		String queryParams= "?limit="+limitValue+"&from="+fromVersion;

		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID4 + "/versions", HTTP_DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", queryParams);
		JsonObject jsonObject = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		String errorMessage = String.format("Invalid limit. Given limit count %d, exceeds the record versions count specified by the given 'from' version '%d'", limitValue, fromVersion);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
		assertEquals(HttpStatus.SC_BAD_REQUEST, jsonObject.get("code").getAsInt());
		assertEquals("Invalid limit.", jsonObject.get("reason").getAsString());
		assertEquals(errorMessage, jsonObject.get("message").getAsString());
	}


}
