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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.ConfigUtils;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class RecordsApiAcceptanceTests extends TestBase {

	private static final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:" + System.currentTimeMillis();
	private static final String RECORD_NEW_ID = TenantUtils.getTenantName() + ":inttest:"
			+ System.currentTimeMillis();
	
	private static final String RECORD_ID_3 = TenantUtils.getTenantName() + ":inttest:testModifyTimeUser-" + System.currentTimeMillis();

	static final String KIND = TenantUtils.getTenantName() + ":ds:inttest:1.0."
			+ System.currentTimeMillis();
	private static final String KIND_WITH_OTHER_TENANT = "tenant1" + ":ds:inttest:1.0."
			+ System.currentTimeMillis();

	static String LEGAL_TAG = LegalTagUtils.createRandomName();

	private static final TokenTestUtils TOKEN_TEST_UTILS = new TokenTestUtils();

	@BeforeAll
	public static void classSetup() throws Exception {
		RecordsApiAcceptanceTests.classSetup(TOKEN_TEST_UTILS.getToken());
	}

	@AfterAll
	public static void classTearDown() throws Exception {
		RecordsApiAcceptanceTests.classTearDown(TOKEN_TEST_UTILS.getToken());
	}

	@BeforeEach
	@Override
	public void setup() throws Exception {
		this.testUtils = new TokenTestUtils();
		this.configUtils = new ConfigUtils("test.properties");
	}

	@AfterEach
	@Override
	public void tearDown() throws Exception {
		this.testUtils = null;
	}

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG, token);
		String jsonInput = createJsonBody(RECORD_ID, "tian");

		TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput, "");
	}

	public static void classTearDown(String token) throws Exception {
		// attempt to cleanup both records used during tests no matter what state they
		// are in
		TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_NEW_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID_3, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		LegalTagUtils.delete(LEGAL_TAG, token);
	}

	@Test
	public void should_createNewRecord_when_givenValidRecord_and_verifyNoAncestry() throws Exception {
		String jsonInput = createJsonBody(RECORD_NEW_ID, "Flor�");

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
		String json = EntityUtils.toString(response.getEntity());
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
		assertTrue(response.getEntity().getContentType().contains("application/json"));

		Gson gson = new Gson();
		DummyRecordsHelper.CreateRecordResponse result = gson.fromJson(json,
				DummyRecordsHelper.CreateRecordResponse.class);

		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(RECORD_NEW_ID, result.recordIds[0]);

		response = TestUtils.send("records/" + RECORD_NEW_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);
		assertEquals("Flor?", recordResult.data.get("name"));
		assertEquals(null, recordResult.data.get("ancestry"));
	}

	@Test
	public void should_updateRecordsWithSameData_when_skipDupesIsFalse() throws Exception {

		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);

		String jsonInput = createJsonBody(RECORD_ID, "tianNew");

		// make update with different name
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "?skipdupes=true");
		DummyRecordsHelper.CreateRecordResponse result = TestUtils.getResult(response, HttpStatus.SC_CREATED,
				DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result);
		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(0, result.skippedRecordIds.length);
		assertEquals(RECORD_ID, result.recordIds[0]);

		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult2 = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);
		assertNotEquals(recordResult.version, recordResult2.version);
		assertEquals("tianNew", recordResult2.data.get("name"));

		// use skip dupes to skip update
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "?skipdupes=true");
		result = TestUtils.getResult(response, HttpStatus.SC_CREATED, DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result);
		assertEquals(1, result.recordCount);
		assertNull(result.recordIds);
		assertNull(result.recordIdVersions);
		assertEquals(
				1,
				result.skippedRecordIds.length,
				"Expected to skip the update when the data was the same as previous update and skipdupes is true"
		);
		assertEquals(RECORD_ID, result.skippedRecordIds[0]);

		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult3 = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);
		assertEquals(recordResult2.version, recordResult3.version);
		assertEquals("tianNew", recordResult3.data.get("name"));

		// set skip dupes to false to make the update with same data
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "?skipdupes=false");
		result = TestUtils.getResult(response, HttpStatus.SC_CREATED, DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result);
		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(
				0,
				result.skippedRecordIds.length,
				"Expected to NOT skip the update when data is the same but skipdupes is false"
		);
		assertEquals(RECORD_ID, result.recordIds[0]);

		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		recordResult3 = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);
		assertNotEquals(recordResult2.version, recordResult3.version);
		assertEquals("tianNew", recordResult3.data.get("name"));
	}

	@Test
	public void should_getAnOlderVersion_and_theMostRecentVersion_and_retrieveAllVersions()
			throws Exception {

		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse originalRecordResult = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);

		String jsonInput = createJsonBody(RECORD_ID, "tianNew2");

		// add an extra version
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
		TestUtils.getResult(response, HttpStatus.SC_CREATED, DummyRecordsHelper.CreateRecordResponse.class);

		// get a specific older version and validate it is the same
		response = TestUtils.send("records/" + RECORD_ID + "/" + originalRecordResult.version, "GET",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResultVersion = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);
		assertEquals(originalRecordResult.id, recordResultVersion.id);
		assertEquals(originalRecordResult.version, recordResultVersion.version);
		assertEquals(originalRecordResult.data.get("name"), recordResultVersion.data.get("name"));

		// get the latest version by using id and validate it has the latest data
		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse newRecordResult = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);
		assertEquals(originalRecordResult.id, newRecordResult.id);
		assertNotEquals(originalRecordResult.version, newRecordResult.version);
		assertEquals("tianNew2", newRecordResult.data.get("name"));

		// older version and new version should be found
		response = TestUtils.send("records/versions/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetVersionsResponse versionsResponse = TestUtils.getResult(response, HttpStatus.SC_OK, GetVersionsResponse.class);
		assertEquals(RECORD_ID, versionsResponse.recordId);
		List<Long> versions = Arrays.asList(versionsResponse.versions);
		assertTrue(versions.contains(originalRecordResult.version));
		assertTrue(versions.contains(newRecordResult.version));
	}

	@Test
	public void should_deleteAllVersionsOfARecord_when_deletingARecordById() throws Exception {
		String idToDelete = RECORD_ID + 1;

		String jsonInput = createJsonBody(idToDelete, "tianNew2");

		// add an extra version
		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
		TestUtils.getResult(response, HttpStatus.SC_CREATED, DummyRecordsHelper.CreateRecordResponse.class);

		response = TestUtils.send("records/" + idToDelete, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());

		response = TestUtils.send("records/" + idToDelete, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		String notFoundResponse = TestUtils.getResult(response, 404, String.class);
		assertEquals("{\"code\":404,\"reason\":\"Record not found\",\"message\":\"The record" + " '" + idToDelete + "' "
				+ "was not found\"}", notFoundResponse);
	}

	@Test
	public void should_ingestRecord_when_noRecordIdIsProvided() throws Exception {
		String body = createJsonBody(null, "Foo");

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "");
		String responseString = TestUtils.getResult(response, HttpStatus.SC_CREATED, String.class);
		JsonObject responseJson = new JsonParser().parse(responseString).getAsJsonObject();

		assertEquals(1, responseJson.get("recordCount").getAsInt());
		assertEquals(1, responseJson.get("recordIds").getAsJsonArray().size());
		assertTrue(responseJson.get("recordIds").getAsJsonArray().get(0).getAsString()
				.startsWith(TenantUtils.getTenantName() + ":"));
	}

	@Test
	public void should_returnWholeRecord_when_recordIsIngestedWithAllFields() throws Exception {
		final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:wholerecord-" + System.currentTimeMillis();

		String body = createJsonBody(RECORD_ID, "Foo");

		// injesting record
		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "");
		TestUtils.getResult(response, HttpStatus.SC_CREATED, String.class);

		// getting record
		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		String responseString = TestUtils.getResult(response, HttpStatus.SC_OK, String.class);

		JsonObject responseJson = JsonParser.parseString(responseString).getAsJsonObject();

		assertEquals(RECORD_ID, responseJson.get("id").getAsString());

		assertEquals(KIND, responseJson.get("kind").getAsString());

		JsonObject acl = responseJson.get("acl").getAsJsonObject();
		assertEquals(TestUtils.getAcl(), acl.get("owners").getAsString());
		assertEquals(TestUtils.getAcl(), acl.get("viewers").getAsString());

		assertEquals("Foo", responseJson.getAsJsonObject("data").get("name").getAsString());
	}

	@Test
	public void should_returnWholeRecord_when_recordIsIngestedWithOtherTenantInKind() throws Exception {
		final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:wholerecord-" + System.currentTimeMillis();
		String body = createJsonBody(RECORD_ID, "Foo", KIND_WITH_OTHER_TENANT);
		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "");
		TestUtils.getResult(response, HttpStatus.SC_CREATED, String.class);
		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		String responseString = TestUtils.getResult(response, HttpStatus.SC_OK, String.class);
		JsonObject responseJson = JsonParser.parseString(responseString).getAsJsonObject();
		assertEquals(RECORD_ID, responseJson.get("id").getAsString());
		assertEquals(KIND_WITH_OTHER_TENANT, responseJson.get("kind").getAsString());
		JsonObject acl = responseJson.get("acl").getAsJsonObject();
		assertEquals(TestUtils.getAcl(), acl.get("owners").getAsString());
		assertEquals(TestUtils.getAcl(), acl.get("viewers").getAsString());
		assertEquals("Foo", responseJson.getAsJsonObject("data").get("name").getAsString());
	}

	@Test
	public void should_insertNewRecord_when_skipDupesIsTrue() throws Exception {
		final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:wholerecord-" + System.currentTimeMillis();
		String body = createJsonBody(RECORD_ID, "Foo");
		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "?skipdupes=true");
		DummyRecordsHelper.CreateRecordResponse result = TestUtils.getResult(response, HttpStatus.SC_CREATED, DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result);
		assertEquals(1, result.recordCount);
		assertEquals(
				1,
				result.recordIds.length,
				"Expected to insert the new record when skipdupes is true"
		);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(RECORD_ID, result.recordIds[0]);
		response = TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());
	}

	@Test
	public void should_createNewRecord_withSpecialCharacter_ifEnabled() throws Exception {
		final long currentTimeMillis = System.currentTimeMillis();
		final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:testSpecialChars%abc%2Ffoobar-" + currentTimeMillis;
		final String ENCODED_RECORD_ID = TenantUtils.getTenantName() + ":inttest:testSpecialChars%25abc%252Ffoobar-" + currentTimeMillis;

		String jsonInput = createJsonBody(RECORD_ID, "TestSpecialCharacters");

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
		String json = EntityUtils.toString(response.getEntity());
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
		assertTrue(response.getEntity().getContentType().contains("application/json"));

		Gson gson = new Gson();
		DummyRecordsHelper.CreateRecordResponse result = gson.fromJson(json,
				DummyRecordsHelper.CreateRecordResponse.class);

		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(RECORD_ID, result.recordIds[0]);

		response = TestUtils.send("records/" + ENCODED_RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");

		// If encoded percent is true, the request should go through and should be able to get a successful response.
		if (configUtils != null && configUtils.getBooleanProperty("enableEncodedSpecialCharactersInURL", "false")) {
			GetRecordResponse recordResult = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);
			assertEquals("TestSpecialCharacters", recordResult.data.get("name"));
		} else {
			// Service does not allow URLs with suspicious characters, Which is the default setting.
			// Different CSPs are responding with different status code for this error when a special character like %25 is present in the URL.
			// Hence the Assert Statement is marked not to be 200.
			// More details - https://community.opengroup.org/osdu/platform/system/storage/-/issues/61
			assertNotEquals(HttpStatus.SC_OK, response.getCode());
		}

	}

	@Test
	public void should_updateModifyTimeWithRecordUpdate() throws Exception {

		String jsonInput = createJsonBody(RECORD_ID_3, "tianNew");

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "?skipdupes=false");
		DummyRecordsHelper.CreateRecordResponse result = TestUtils.getResult(response, HttpStatus.SC_CREATED,
				DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result);
		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(0, result.skippedRecordIds.length);
		assertEquals(RECORD_ID_3, result.recordIds[0]);
		String firstVersionNumber = StringUtils.substringAfterLast(result.recordIdVersions[0],":");

		response = TestUtils.send("records/" + RECORD_ID_3, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult1 = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);

		//No modify user and time in 1st version of record
		assertNull(recordResult1.modifyTime);
		assertNull(recordResult1.modifyUser);

		// make update-1
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "?skipdupes=false");
		DummyRecordsHelper.CreateRecordResponse result2 = TestUtils.getResult(response, HttpStatus.SC_CREATED,
				DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result2);
		assertEquals(1, result2.recordCount);
		assertEquals(1, result2.recordIds.length);
		assertEquals(1, result2.recordIdVersions.length);
		assertEquals(0, result2.skippedRecordIds.length);
		assertEquals(RECORD_ID_3, result2.recordIds[0]);
		String secondVersionNumber = StringUtils.substringAfterLast(result2.recordIdVersions[0],":");

		// make update-2
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "?skipdupes=false");
		DummyRecordsHelper.CreateRecordResponse result3 = TestUtils.getResult(response, HttpStatus.SC_CREATED, DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result3);
		assertEquals(1, result3.recordCount);
		assertEquals(1, result3.recordIds.length);
		assertEquals(1, result3.recordIdVersions.length);
		assertEquals(0, result3.skippedRecordIds.length);
		assertEquals(RECORD_ID_3, result3.recordIds[0]);

		String thirdLastVersionNumber = StringUtils.substringAfterLast(result3.recordIdVersions[0],":");
		response = TestUtils.send("records/" + RECORD_ID_3+"/"+firstVersionNumber, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult2 = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);

		//No modify user and time in 1st version of record
		assertNull(recordResult2.modifyTime);
		assertNull(recordResult2.modifyUser);

		response = TestUtils.send("records/" + RECORD_ID_3+"/"+secondVersionNumber, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult3 = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);

		response = TestUtils.send("records/" + RECORD_ID_3+"/"+thirdLastVersionNumber, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult4 = TestUtils.getResult(response, HttpStatus.SC_OK, GetRecordResponse.class);

		//modify time is different for each version of record
		assertNotEquals(recordResult4.modifyTime, recordResult3.modifyTime);


	}

	protected static String createJsonBody(String id, String name) {
		return "[" + singleEntityBody(id, name, KIND, LEGAL_TAG) + "]";
	}

	protected static String createJsonBody(String id, String name, String kind) {
		return "[" + singleEntityBody(id, name, kind, LEGAL_TAG) + "]";
	}

	public class RecordAncestry {
		public String[] parents;
	}

	protected class GetVersionsResponse {
		String recordId;
		Long versions[];
	}

	protected class GetRecordResponse {
		String id;
		long version;
		Map<String, Object> data;
		String modifyTime;
		String modifyUser;
	}

	public static String singleEntityBody(String id, String name, String kind, String legalTagName) {

		JsonObject data = new JsonObject();
		data.addProperty("name", name);

		JsonObject acl = new JsonObject();
		JsonArray acls = new JsonArray();
		acls.add(TestUtils.getAcl());
		acl.add("viewers", acls);
		acl.add("owners", acls);

		JsonObject legal = new JsonObject();
		JsonArray legals = new JsonArray();
		legals.add(legalTagName);
		legal.add("legaltags", legals);
		JsonArray ordc = new JsonArray();
		ordc.add("BR");
		legal.add("otherRelevantDataCountries", ordc);

		JsonObject record = new JsonObject();
		if (!Strings.isNullOrEmpty(id)) {
			record.addProperty("id", id);
		}

		record.addProperty("kind", kind);
		record.add("acl", acl);
		record.add("legal", legal);
		record.add("data", data);

		return record.toString();
	}
}
