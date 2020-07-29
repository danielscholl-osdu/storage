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

import java.util.Map;

import org.apache.http.HttpStatus;
import org.junit.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;

public abstract class RecordAccessAuthorizationTests extends TestBase {

	protected static long NOW = System.currentTimeMillis();
	protected static String LEGAL_TAG = LegalTagUtils.createRandomName();
	protected static String KIND = TenantUtils.getTenantName() + ":dataaccess:no:1.1." + NOW;
	protected static String RECORD_ID = TenantUtils.getTenantName() + ":dataaccess:1.1." + NOW;

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG, token);

		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token),
				RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");

		assertEquals(HttpStatus.SC_CREATED, response.getStatus());
	}

	public static void classTearDown(String token) throws Exception {
		LegalTagUtils.delete(LEGAL_TAG, token);

		TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
	}

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToGetLatestVersionOfARecord() throws Exception {
		Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		ClientResponse response = TestUtils.send("records/" + RECORD_ID, "GET", headers, "", "");

		this.assertNotAuthorized(response);
	}

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToListVersionsOfARecord() throws Exception {
		Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		ClientResponse response = TestUtils.send("records/versions/" + RECORD_ID, "GET", headers, "", "");

		this.assertNotAuthorized(response);
	}

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToGetSpecificVersionOfARecord() throws Exception {
		Map<String, String> withDataAccessHeader = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getToken());

		ClientResponse response = TestUtils.send("records/versions/" + RECORD_ID, "GET", withDataAccessHeader, "", "");
		JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
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

		ClientResponse response = TestUtils.send("records/", "POST", headers, "{'anything':'anything'}",
				RECORD_ID + ":delete");

		this.assertNotAuthorized(response);
	}

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToPurgeRecord() throws Exception {
		Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		ClientResponse response = TestUtils.send("records/" + RECORD_ID, "DELETE", headers, "", "");

        assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatus());
        JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
        assertEquals(403, json.get("code").getAsInt());
        assertEquals("Access denied", json.get("reason").getAsString());
        assertEquals("The user is not authorized to purge the record", json.get("message").getAsString());
    }

	@Test
	public void should_receiveHttp403_when_userIsNotAuthorizedToUpdateARecord() throws Exception {
		Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		ClientResponse response = TestUtils.send("records", "PUT", headers,
				RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");

		this.assertNotAuthorized(response);
	}

	@Test
	public void should_NoneRecords_when_fetchingMultipleRecords_and_notAuthorizedToRecords()
			throws Exception {

		// Creates a new record
		String newRecordId = TenantUtils.getTenantName() + ":dataaccess:2.2." + NOW;

		Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(),
				testUtils.getNoDataAccessToken());

		ClientResponse response = TestUtils.send("records", "PUT", headers,
				RecordUtil.createDefaultJsonRecord(newRecordId, KIND, LEGAL_TAG), "");

		assertEquals(HttpStatus.SC_CREATED, response.getStatus());

		// Query for original record (no access) and recently created record (with
		// access)
		JsonArray records = new JsonArray();
		records.add(RECORD_ID);
		records.add(newRecordId);

		JsonObject body = new JsonObject();
		body.add("records", records);

		response = TestUtils.send("query/records", "POST", headers, body.toString(), "");
		assertEquals(HttpStatus.SC_OK, response.getStatus());

		DummyRecordsHelper.RecordsMock responseObject = new DummyRecordsHelper().getRecordsMockFromResponse(response);

		assertEquals(0, responseObject.records.length);
		assertEquals(0, responseObject.invalidRecords.length);
		assertEquals(0, responseObject.retryRecords.length);

		TestUtils.send("records/" + newRecordId, "DELETE", headers, "", "");
	}

	protected void assertNotAuthorized(ClientResponse response) {
		assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatus());
		JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
		assertEquals(403, json.get("code").getAsInt());
		assertEquals("Access denied", json.get("reason").getAsString());
		assertEquals("The user is not authorized to perform this action", json.get("message").getAsString());
	}
}
