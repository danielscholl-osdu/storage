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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;
import org.apache.http.HttpStatus;
import org.junit.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;

public abstract class RecordsApiAcceptanceTests extends TestBase {

	protected static final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:" + System.currentTimeMillis();
	protected static final String RECORD_NEW_ID = TenantUtils.getTenantName() + ":inttest:"
			+ System.currentTimeMillis();
	protected static final String KIND = TenantUtils.getTenantName() + ":ds:inttest:1.0."
			+ System.currentTimeMillis();
	protected static final String KIND_WITH_OTHER_TENANT = "tenant1" + ":ds:inttest:1.0."
			+ System.currentTimeMillis();

	protected static String LEGAL_TAG = LegalTagUtils.createRandomName();

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
		LegalTagUtils.delete(LEGAL_TAG, token);
	}

	@Test
	public void should_createNewRecord_when_givenValidRecord_and_verifyNoAncestry() throws Exception {
		String jsonInput = createJsonBody(RECORD_NEW_ID, "Flor�");

		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
		String json = response.getEntity(String.class);
		assertEquals(201, response.getStatus());
		assertEquals("application/json; charset=UTF-8", response.getType().toString());

		Gson gson = new Gson();
		DummyRecordsHelper.CreateRecordResponse result = gson.fromJson(json,
				DummyRecordsHelper.CreateRecordResponse.class);

		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(RECORD_NEW_ID, result.recordIds[0]);

		response = TestUtils.send("records/" + RECORD_NEW_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult = TestUtils.getResult(response, 200, GetRecordResponse.class);
		assertEquals("Flor�", recordResult.data.get("name"));
		assertEquals(null, recordResult.data.get("ancestry"));
	}

	@Test
	public void should_updateRecordsWithSameData_when_skipDupesIsFalse() throws Exception {

		ClientResponse response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult = TestUtils.getResult(response, 200, GetRecordResponse.class);

		String jsonInput = createJsonBody(RECORD_ID, "tianNew");

		// make update with different name
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "?skipdupes=true");
		DummyRecordsHelper.CreateRecordResponse result = TestUtils.getResult(response, 201,
				DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result);
		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(0, result.skippedRecordIds.length);
		assertEquals(RECORD_ID, result.recordIds[0]);

		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult2 = TestUtils.getResult(response, 200, GetRecordResponse.class);
		assertNotEquals(recordResult.version, recordResult2.version);
		assertEquals("tianNew", recordResult2.data.get("name"));

		// use skip dupes to skip update
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "?skipdupes=true");
		result = TestUtils.getResult(response, 201, DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result);
		assertEquals(1, result.recordCount);
		assertNull(result.recordIds);
		assertNull(result.recordIdVersions);
		assertEquals("Expected to skip the update when the data was the same as previous update and skipdupes is true",
				1, result.skippedRecordIds.length);
		assertEquals(RECORD_ID, result.skippedRecordIds[0]);

		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResult3 = TestUtils.getResult(response, 200, GetRecordResponse.class);
		assertEquals(recordResult2.version, recordResult3.version);
		assertEquals("tianNew", recordResult3.data.get("name"));

		// set skip dupes to false to make the update with same data
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "?skipdupes=false");
		result = TestUtils.getResult(response, 201, DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result);
		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals("Expected to NOT skip the update when data is the same but skipdupes is false", 0,
				result.skippedRecordIds.length);
		assertEquals(RECORD_ID, result.recordIds[0]);

		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		recordResult3 = TestUtils.getResult(response, 200, GetRecordResponse.class);
		assertNotEquals(recordResult2.version, recordResult3.version);
		assertEquals("tianNew", recordResult3.data.get("name"));
	}

	@Test
	public void should_getAnOlderVersion_and_theMostRecentVersion_and_retrieveAllVersions()
			throws Exception {

		ClientResponse response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse originalRecordResult = TestUtils.getResult(response, 200, GetRecordResponse.class);

		String jsonInput = createJsonBody(RECORD_ID, "tianNew2");

		// add an extra version
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
		TestUtils.getResult(response, 201, DummyRecordsHelper.CreateRecordResponse.class);

		// get a specific older version and validate it is the same
		response = TestUtils.send("records/" + RECORD_ID + "/" + originalRecordResult.version, "GET",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse recordResultVersion = TestUtils.getResult(response, 200, GetRecordResponse.class);
		assertEquals(originalRecordResult.id, recordResultVersion.id);
		assertEquals(originalRecordResult.version, recordResultVersion.version);
		assertEquals(originalRecordResult.data.get("name"), recordResultVersion.data.get("name"));

		// get the latest version by using id and validate it has the latest data
		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetRecordResponse newRecordResult = TestUtils.getResult(response, 200, GetRecordResponse.class);
		assertEquals(originalRecordResult.id, newRecordResult.id);
		assertNotEquals(originalRecordResult.version, newRecordResult.version);
		assertEquals("tianNew2", newRecordResult.data.get("name"));

		// older version and new version should be found
		response = TestUtils.send("records/versions/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		GetVersionsResponse versionsResponse = TestUtils.getResult(response, 200, GetVersionsResponse.class);
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
		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
		TestUtils.getResult(response, 201, DummyRecordsHelper.CreateRecordResponse.class);

		response = TestUtils.send("records/" + idToDelete, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());

		response = TestUtils.send("records/" + idToDelete, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		String notFoundResponse = TestUtils.getResult(response, 404, String.class);
		assertEquals("{\"code\":404,\"reason\":\"Record not found\",\"message\":\"The record" + " '" + idToDelete + "' "
				+ "was not found\"}", notFoundResponse);
	}

	@Test
	public void should_ingestRecord_when_noRecordIdIsProvided() throws Exception {
		String body = createJsonBody(null, "Foo");

		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "");
		String responseString = TestUtils.getResult(response, 201, String.class);
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
		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "");
		TestUtils.getResult(response, 201, String.class);

		// getting record
		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		String responseString = TestUtils.getResult(response, 200, String.class);

		JsonObject responseJson = new JsonParser().parse(responseString).getAsJsonObject();

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
		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "");
		TestUtils.getResult(response, 201, String.class);
		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		String responseString = TestUtils.getResult(response, 200, String.class);
		JsonObject responseJson = new JsonParser().parse(responseString).getAsJsonObject();
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
		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "?skipdupes=true");
		DummyRecordsHelper.CreateRecordResponse result = TestUtils.getResult(response, 201, DummyRecordsHelper.CreateRecordResponse.class);
		assertNotNull(result);
		assertEquals(1, result.recordCount);
		assertEquals("Expected to insert the new record when skipdupes is true", 1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(RECORD_ID, result.recordIds[0]);
		response = TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());
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