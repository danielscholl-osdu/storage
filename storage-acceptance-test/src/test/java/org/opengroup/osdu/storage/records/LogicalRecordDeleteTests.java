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

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.DummyRecordsHelper.CreateRecordResponse;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class LogicalRecordDeleteTests extends TestBase {

	private static final long NOW = System.currentTimeMillis();
	private static final String KIND = TenantUtils.getTenantName() + ":delete:inttest:1.0." + NOW;
	private static final String LEGAL_TAG = LegalTagUtils.createRandomName();
	private static final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:" + NOW;

	private static final TokenTestUtils TOKEN_TEST_UTILS = new TokenTestUtils();

	@BeforeAll
	public static void classSetup() throws Exception {
		LogicalRecordDeleteTests.classSetup(TOKEN_TEST_UTILS.getToken());
	}

	@AfterAll
	public static void classTearDown() throws Exception {
		LogicalRecordDeleteTests.classTearDown(TOKEN_TEST_UTILS.getToken());
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

		String body = createBody(RECORD_ID, "anything", Lists.newArrayList(LEGAL_TAG), Lists.newArrayList("BR", "IT"));

		CloseableHttpResponse response = TestUtils.send("records", "PUT",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), body, "");

		String responseBody = EntityUtils.toString(response.getEntity());
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
		assertTrue(response.getEntity().getContentType().contains("application/json"));

		Gson gson = new Gson();
		CreateRecordResponse result = gson.fromJson(responseBody, CreateRecordResponse.class);

		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(RECORD_ID, result.recordIds[0]);
	}

	public static void classTearDown(String token) throws Exception {
		TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");

		LegalTagUtils.delete(LEGAL_TAG, token);
	}

	@Test
	public void should_notRetrieveRecord_and_notDeleteRecordAgain_when_deletingItLogically() throws Exception {
		String queryParam = String.format("records/%s:delete", RECORD_ID);

		// deleting
		CloseableHttpResponse response = TestUtils.send(queryParam, "POST",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "{'anything':'teste'}", "");
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());

		// trying to get
		response = TestUtils.send("records/" + RECORD_ID, "GET",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getCode());

		// trying to delete again
		response = TestUtils.send(queryParam, "POST",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "{'anything':'teste'}", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getCode());
	}

	protected static String createBody(String id, String dataValue, List<String> legalTags, List<String> ordc) {
		JsonObject data = new JsonObject();
		data.addProperty("name", dataValue);

		JsonObject acl = new JsonObject();
		JsonArray acls = new JsonArray();
		acls.add(TestUtils.getAcl());
		acl.add("viewers", acls);
		acl.add("owners", acls);

		JsonArray tags = new JsonArray();
		legalTags.forEach(t -> tags.add(t));

		JsonArray ordcJson = new JsonArray();
		ordc.forEach(o -> ordcJson.add(o));

		JsonObject legal = new JsonObject();
		legal.add("legaltags", tags);
		legal.add("otherRelevantDataCountries", ordcJson);

		JsonObject record = new JsonObject();
		record.addProperty("id", id);
		record.addProperty("kind", KIND);
		record.add("acl", acl);
		record.add("legal", legal);
		record.add("data", data);

		JsonArray records = new JsonArray();
		records.add(record);

		return records.toString();
	}
}
