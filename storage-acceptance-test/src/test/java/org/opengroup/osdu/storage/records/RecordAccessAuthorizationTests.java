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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Map;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;


public final class RecordAccessAuthorizationTests extends TestBase {

	private static long NOW = System.currentTimeMillis();
	private static String LEGAL_TAG = LegalTagUtils.createRandomName();
	private static String KIND = TenantUtils.getTenantName() + ":dataaccess:no:1.1." + NOW;
	private static String RECORD_ID = TenantUtils.getTenantName() + ":no:1.1." + NOW;

	private static final TokenTestUtils TOKEN_TEST_UTILS = new TokenTestUtils();

	@BeforeAll
	public static void classSetup() throws Exception {
		RecordAccessAuthorizationTests.classSetup(TOKEN_TEST_UTILS.getToken());
	}

	@AfterAll
	public static void classTearDown() throws Exception {
		RecordAccessAuthorizationTests.classTearDown(TOKEN_TEST_UTILS.getToken());
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

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token),
				RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");

		assertEquals(HttpStatus.SC_CREATED, response.getCode());
	}

	public static void classTearDown(String token) throws Exception {
		LegalTagUtils.delete(LEGAL_TAG, token);

		TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
	}

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToGetLatestVersionOfARecord() throws Exception {
		Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, "GET", headers, "", "");

		this.assertNotAuthorized(response);
	}

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToListVersionsOfARecord() throws Exception {
		Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		CloseableHttpResponse response = TestUtils.send("records/versions/" + RECORD_ID, "GET", headers, "", "");

		this.assertNotAuthorized(response);
	}

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToGetSpecificVersionOfARecord() throws Exception {
		Map<String, String> withDataAccessHeader = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getToken());

		CloseableHttpResponse response = TestUtils.send("records/versions/" + RECORD_ID, "GET", withDataAccessHeader, "", "");
		JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		String version = json.get("versions").getAsJsonArray().get(0).toString();

		Map<String, String> withoutDataAccessHeader = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		response = TestUtils.send("records/" + RECORD_ID + "/" + version, "GET", withoutDataAccessHeader, "", "");

		this.assertNotAuthorized(response);
	}

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToDeleteRecord() throws Exception {
		Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		CloseableHttpResponse response = TestUtils.send("records/", "POST", headers, "{'anything':'anything'}",
				RECORD_ID + ":delete");

		this.assertNotAuthorized(response);
	}

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToPurgeRecord() throws Exception {
		Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, "DELETE", headers, "", "");

        assertEquals(HttpStatus.SC_FORBIDDEN, response.getCode());
        JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
        assertEquals(HttpStatus.SC_FORBIDDEN, json.get("code").getAsInt());
        assertEquals("Access denied", json.get("reason").getAsString());
    }

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToUpdateARecord() throws Exception {
		Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		CloseableHttpResponse response = TestUtils.send("records", "PUT", headers,
				RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");

		this.assertNotAuthorized(response);
	}

	@Test
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
		assertEquals(HttpStatus.SC_OK, response.getCode());

		DummyRecordsHelper.RecordsMock responseObject = new DummyRecordsHelper().getRecordsMockFromResponse(response);

		assertEquals(0, responseObject.records.length);
		assertEquals(0, responseObject.invalidRecords.length);
		assertEquals(0, responseObject.retryRecords.length);

		TestUtils.send("records/" + newRecordId, "DELETE", headersWithNoDataAccessToken, "", "");
	}

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
		assertEquals(HttpStatus.SC_FORBIDDEN, json.get("code").getAsInt());
		assertEquals("Access denied", json.get("reason").getAsString());
		assertEquals("The user is not authorized to perform this action", json.get("message").getAsString());
	}
}
