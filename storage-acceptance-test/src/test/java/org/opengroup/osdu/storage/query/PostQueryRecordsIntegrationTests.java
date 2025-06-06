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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
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


public final class PostQueryRecordsIntegrationTests extends TestBase {

	private static final long NOW = System.currentTimeMillis();

	private static final String RECORD_ID = TenantUtils.getTenantName() + ":query:" + NOW;
	private static final String KIND = TenantUtils.getTenantName() + ":ds:query:1.0." + NOW;
	private static final String LEGAL_TAG = LegalTagUtils.createRandomName();
	private static final DummyRecordsHelper RECORDS_HELPER = new DummyRecordsHelper();
	private static final TokenTestUtils TOKEN_TEST_UTILS = new TokenTestUtils();

	@BeforeAll
	public static void classSetup() throws Exception {
		PostQueryRecordsIntegrationTests.classSetup(TOKEN_TEST_UTILS.getToken());
	}

	@AfterAll
	public static void classTearDown() throws Exception {
		PostQueryRecordsIntegrationTests.classTearDown(TOKEN_TEST_UTILS.getToken());
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
		String jsonInput = RecordUtil.createDefaultJsonRecords(3, RECORD_ID, KIND, LEGAL_TAG);

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput, "");
		CloseableHttpResponse modifyRecordsResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput, "");
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
		assertEquals(HttpStatus.SC_CREATED, modifyRecordsResponse.getCode());
	}

	public static void classTearDown(String token) throws Exception {
		TestUtils.send("records/" + RECORD_ID + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID + 1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID + 2, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");

		LegalTagUtils.delete(LEGAL_TAG, token);
	}

	@Test
	public void should_returnSingleRecordMatching_when_givenIdAndNoAttributes() throws Exception {
		JsonArray attributes = new JsonArray();
		JsonArray records = new JsonArray();
		records.add(RECORD_ID + 0);

		JsonObject body = new JsonObject();
		body.add("records", records);
		body.add("attributes", attributes);

		CloseableHttpResponse response = TestUtils.send("query/records", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body.toString(),
				"");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		DummyRecordsHelper.RecordsMock responseObject = RECORDS_HELPER.getRecordsMockFromResponse(response);

		assertEquals(1, responseObject.records.length);
		assertEquals(0, responseObject.invalidRecords.length);
		assertEquals(0, responseObject.retryRecords.length);

		assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
		assertEquals(RECORD_ID + 0, responseObject.records[0].id);
		assertEquals(KIND, responseObject.records[0].kind);
		assertTrue(responseObject.records[0].createUser != null && responseObject.records[0].createTime != null);
		assertTrue(responseObject.records[0].modifyUser != null && responseObject.records[0].modifyTime != null);
		assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
		assertEquals(3, responseObject.records[0].data.size());
	}

	@Test
	public void should_returnOnlyRequestedDataProperties_when_specificAttributesAreGiven() throws Exception {
		JsonArray attributes = new JsonArray();
		attributes.add("data.count");
		JsonArray records = new JsonArray();
		records.add(RECORD_ID + 1);

		JsonObject body = new JsonObject();
		body.add("records", records);
		body.add("attributes", attributes);

		CloseableHttpResponse response = TestUtils.send("query/records", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body.toString(),
				"");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		DummyRecordsHelper.RecordsMock responseObject = RECORDS_HELPER.getRecordsMockFromResponse(response);

		assertEquals(1, responseObject.records[0].data.size());
		assertEquals("1.23456789E8", responseObject.records[0].data.get("count").toString());
	}

	@Test
	public void should_returnMultipleRecordsMatchingGivenIds_when_noAttributesAreGiven() throws Exception {
		JsonArray attributes = new JsonArray();
		JsonArray records = new JsonArray();
		records.add(RECORD_ID + 0);
		records.add(RECORD_ID + 1);
		records.add(RECORD_ID + 2);

		JsonObject body = new JsonObject();
		body.add("records", records);
		body.add("attributes", attributes);

		CloseableHttpResponse response = TestUtils.send("query/records", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body.toString(),
				"");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		DummyRecordsHelper.RecordsMock responseObject = RECORDS_HELPER.getRecordsMockFromResponse(response);

		assertEquals(3, responseObject.records.length);
		assertEquals(0, responseObject.invalidRecords.length);
		assertEquals(0, responseObject.retryRecords.length);

		String[] ids = ArrayUtils.addAll(new String[] { responseObject.records[0].id }, responseObject.records[1].id,
				responseObject.records[2].id);
		assertTrue(Arrays.asList(ids).contains(RECORD_ID + 0));
		assertTrue(Arrays.asList(ids).contains(RECORD_ID + 1));
		assertTrue(Arrays.asList(ids).contains(RECORD_ID + 2));
	}

	@Test
	public void should_returnInvalidRecord_when_nonExistingIDGiven() throws Exception {
		JsonArray attributes = new JsonArray();
		JsonArray records = new JsonArray();
		records.add("nonexisting:id");

		JsonObject body = new JsonObject();
		body.add("records", records);
		body.add("attributes", attributes);

		CloseableHttpResponse response = TestUtils.send("query/records", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body.toString(),
				"");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		DummyRecordsHelper.RecordsMock responseObject = RECORDS_HELPER.getRecordsMockFromResponse(response);

		assertEquals(0, responseObject.records.length);
		assertEquals(1, responseObject.invalidRecords.length);
		assertEquals("nonexisting:id", responseObject.invalidRecords[0]);
		assertEquals(0, responseObject.retryRecords.length);
	}
}
