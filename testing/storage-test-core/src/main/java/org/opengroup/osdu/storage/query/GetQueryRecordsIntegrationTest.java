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

package org.opengroup.osdu.storage.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.junit.*;

import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;

public abstract class GetQueryRecordsIntegrationTest extends TestBase {

	protected static final long NOW = System.currentTimeMillis();

	protected static final String RECORD_ID = TenantUtils.getTenantName() + ":query:" + NOW;
	protected static final String KIND = TenantUtils.getTenantName() + ":ds:query:1.0." + NOW;
	protected static final String LEGAL_TAG = LegalTagUtils.createRandomName();
	protected static final DummyRecordsHelper RECORDS_HELPER = new DummyRecordsHelper();

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG, token);
		String jsonInput = RecordUtil.createDefaultJsonRecords(5, RECORD_ID, KIND, LEGAL_TAG);

		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput, "");
		assertEquals(201, response.getStatus());
	}

	public static void classTearDown(String token) throws Exception {
		TestUtils.send("records/" + RECORD_ID + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID + 1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID + 2, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID + 3, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID + 4, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");

		LegalTagUtils.delete(LEGAL_TAG, token);
	}

	@Test
	public void should_return5Ids_when_requestingKindThatHas5Entries() throws Exception {
		ClientResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?kind=" + KIND);
		assertEquals(HttpStatus.SC_OK, response.getStatus());

		DummyRecordsHelper.QueryResultMock responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
		assertEquals(5, responseObject.results.length);
	}

	@Test
	public void should_incrementThroughIds_when_requestingKindThatHasMoreThanOneEntry_and_limitIsSetTo2_and_usingPreviousCursorPos()
			throws Exception {

		Set<String> result = new HashSet<>();

		// first call
		ClientResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=2&kind=" + KIND);
		if (response.getStatus() != 200) {
			fail(formResponseCheckingMessage(response));
		}
		DummyRecordsHelper.QueryResultMock responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
		assertEquals(2, responseObject.results.length);
		assertFalse(StringUtils.isEmpty(responseObject.cursor));

		result.add(responseObject.results[0]);
		result.add(responseObject.results[1]);

		String cursor = responseObject.cursor;
		String cursorEncoded = URLEncoder.encode(cursor, StandardCharsets.UTF_8.toString());

		// second call
		response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"?limit=2&cursor=" + cursorEncoded + "&kind=" + KIND);
		if (response.getStatus() != 200) {
			fail(formResponseCheckingMessage(response));
		}
		responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
		assertEquals(2, responseObject.results.length);
		assertFalse(StringUtils.isEmpty(responseObject.cursor));

		result.add(responseObject.results[0]);
		result.add(responseObject.results[1]);

		cursor = responseObject.cursor;
		cursorEncoded = URLEncoder.encode(cursor, StandardCharsets.UTF_8.toString());

		// third call
		response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"?limit=2&cursor=" + cursorEncoded + "&kind=" + KIND);
		if (response.getStatus() != 200) {
			fail(formResponseCheckingMessage(response));
		}
		responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
		assertEquals(1, responseObject.results.length);

		result.add(responseObject.results[0]);

		assertEquals(5, result.size());
	}

	@Test
	public void should_returnError400_when_usingKindThatHasBadFormat() throws Exception {
		ClientResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=1&kind=bad:kind");
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
	}

	@Test
	public void should_returnNoResults_when_usingKindThatDoesNotExist() throws Exception {
		ClientResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"?limit=1&kind=nonexisting:kind:formatted:1.0.0");
		assertEquals(HttpStatus.SC_OK, response.getStatus());
		DummyRecordsHelper.QueryResultMock responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);

		assertEquals(0, responseObject.results.length);
	}

	@Test
	public void should_returnError400_when_usingInvalidCursorParameter() throws Exception {
		ClientResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"?limit=1&cursor=MY_BAD_CURSOR&kind=" + RECORDS_HELPER.KIND);
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
	}

	@Test
	public void should_returnError400_when_notProvidingKindParameter() throws Exception {
		ClientResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=1&kind=");
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());

		response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=1");
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
	}

	protected String formResponseCheckingMessage(ClientResponse response) {
		JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
		StringBuilder output = new StringBuilder();
		output.append("API is not acting properly, responde code is: ")
				.append(String.valueOf(response.getStatus()))
				.append(". And the reason is: ")
				.append(json.get("reason").getAsString())
		        .append(response.getHeaders().get("correlation-id"));
		return  output.toString();
	}
}