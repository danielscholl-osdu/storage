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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
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

public final class GetQueryRecordsIntegrationTest extends TestBase {

	private static final long NOW = System.currentTimeMillis();

	private static final String RECORD_ID = TenantUtils.getTenantName() + ":query:" + NOW;
	private static final String KIND = TenantUtils.getTenantName() + ":ds:query:1.0." + NOW;
	private static final String LEGAL_TAG = LegalTagUtils.createRandomName();
	private static final DummyRecordsHelper RECORDS_HELPER = new DummyRecordsHelper();
	private static final TokenTestUtils TOKEN_TEST_UTILS = new TokenTestUtils();

	@BeforeAll
	public static void classSetup() throws Exception {
		GetQueryRecordsIntegrationTest.classSetup(TOKEN_TEST_UTILS.getToken());
	}

	@AfterAll
	public static void classTearDown() throws Exception {
		GetQueryRecordsIntegrationTest.classTearDown(TOKEN_TEST_UTILS.getToken());
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
		String jsonInput = RecordUtil.createDefaultJsonRecords(5, RECORD_ID, KIND, LEGAL_TAG);

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput, "");
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
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
		CloseableHttpResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?kind=" + KIND);
		assertEquals(HttpStatus.SC_OK, response.getCode());

		DummyRecordsHelper.QueryResultMock responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
		assertEquals(5, responseObject.results.length);
	}

	@Test
	public void should_incrementThroughIds_when_requestingKindThatHasMoreThanOneEntry_and_limitIsSetTo2_and_usingPreviousCursorPos()
			throws Exception {

		Set<String> result = new HashSet<>();

		// first call
		CloseableHttpResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=2&kind=" + KIND);
		if (response.getCode() != HttpStatus.SC_OK) {
			fail(formResponseCheckingMessage(response));
		}
		DummyRecordsHelper.QueryResultMock responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
		assertEquals(2, responseObject.results.length);
		assertFalse(StringUtils.isEmpty(responseObject.cursor));

		result.add(responseObject.results[0]);
		result.add(responseObject.results[1]);

		String cursor = responseObject.cursor;

		// second call
		response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"?limit=2&cursor=" + cursor + "&kind=" + KIND);
		if (response.getCode() != HttpStatus.SC_OK) {
			fail(formResponseCheckingMessage(response));
		}
		responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
		assertEquals(2, responseObject.results.length);
		assertFalse(StringUtils.isEmpty(responseObject.cursor));

		result.add(responseObject.results[0]);
		result.add(responseObject.results[1]);

		cursor = responseObject.cursor;

		// third call
		response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"?limit=2&cursor=" + cursor + "&kind=" + KIND);
		if (response.getCode() != HttpStatus.SC_OK) {
			fail(formResponseCheckingMessage(response));
		}
		responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);
		assertEquals(1, responseObject.results.length);

		result.add(responseObject.results[0]);

		assertEquals(5, result.size());
	}

	@Test
	public void should_returnError400_when_usingKindThatHasBadFormat() throws Exception {
		CloseableHttpResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=1&kind=bad:kind");
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
	}

	@Test
	public void should_returnNoResults_when_usingKindThatDoesNotExist() throws Exception {
		CloseableHttpResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"?limit=1&kind=nonexisting:kind:formatted:1.0.0");
		assertEquals(HttpStatus.SC_OK, response.getCode());
		DummyRecordsHelper.QueryResultMock responseObject = RECORDS_HELPER.getQueryResultMockFromResponse(response);

		assertEquals(0, responseObject.results.length);
	}

	@Test
	public void should_returnError400_when_usingInvalidCursorParameter() throws Exception {
		CloseableHttpResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"?limit=1&cursor=MY_BAD_CURSOR&kind=" + RECORDS_HELPER.KIND);
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
	}

	@Test
	public void should_returnError400_when_notProvidingKindParameter() throws Exception {
		CloseableHttpResponse response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=1&kind=");
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());

		response = TestUtils.send("query/records", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=1");
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
	}

	protected String formResponseCheckingMessage(CloseableHttpResponse response) {
		JsonObject json;
		try {
			json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		StringBuilder output = new StringBuilder();
		output.append("API is not acting properly, responde code is: ")
				.append(String.valueOf(response.getCode()))
				.append(". And the reason is: ")
				.append(json.get("reason").getAsString())
		        .append(response.getHeaders("correlation-id")[0]);
		return  output.toString();
	}
}
