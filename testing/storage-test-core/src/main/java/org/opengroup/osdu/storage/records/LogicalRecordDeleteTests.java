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

import java.util.List;

import org.apache.http.HttpStatus;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengroup.osdu.storage.util.*;
import org.opengroup.osdu.storage.util.DummyRecordsHelper.CreateRecordResponse;
import com.sun.jersey.api.client.ClientResponse;

public abstract class LogicalRecordDeleteTests extends TestBase {

	protected static final long NOW = System.currentTimeMillis();
	protected static final String KIND = TenantUtils.getTenantName() + ":delete:inttest:1.0." + NOW;
	protected static final String LEGAL_TAG = LegalTagUtils.createRandomName();
	protected static final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:" + NOW;

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG, token);

		String body = createBody(RECORD_ID, "anything", Lists.newArrayList(LEGAL_TAG), Lists.newArrayList("BR", "IT"));

		ClientResponse response = TestUtils.send("records", "PUT",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), body, "");

		String responseBody = response.getEntity(String.class);
		assertEquals(HttpStatus.SC_CREATED, response.getStatus());
		assertEquals("application/json; charset=UTF-8", response.getType().toString());

		Gson gson = new Gson();
		CreateRecordResponse result = gson.fromJson(responseBody, CreateRecordResponse.class);

		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
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
		ClientResponse response = TestUtils.send(queryParam, "POST",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "{'anything':'teste'}", "");
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());

		// trying to get
		response = TestUtils.send("records/" + RECORD_ID, "GET",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());

		// trying to delete again
		response = TestUtils.send(queryParam, "POST",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "{'anything':'teste'}", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
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