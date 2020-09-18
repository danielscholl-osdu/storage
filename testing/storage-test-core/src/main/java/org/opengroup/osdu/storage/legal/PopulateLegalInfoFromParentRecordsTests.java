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

package org.opengroup.osdu.storage.legal;

import static org.opengroup.osdu.storage.util.LegalTagUtils.createRandomName;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.*;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.opengroup.osdu.storage.util.*;
import org.opengroup.osdu.storage.util.DummyRecordsHelper.CreateRecordResponse;
import org.opengroup.osdu.storage.util.DummyRecordsHelper.RecordResultMock;
import com.sun.jersey.api.client.ClientResponse;

import io.jsonwebtoken.lang.Collections;

public abstract class PopulateLegalInfoFromParentRecordsTests extends TestBase {

	protected static final String KIND = TenantUtils.getTenantName() + ":parent:inttest:1.0."
			+ System.currentTimeMillis();
	protected static String LEGAL_TAG_PARENT_ONE;
	protected static String LEGAL_TAG_PARENT_TWO;
	protected static String LEGAL_TAG_CHILD;
	protected static String LEGAL_TAG_CHILD_THAT_WILL_NOT_BE_CREATED;
	protected static String PARENT_ID_ONE;
	protected static String PARENT_ID_TWO;
	protected static String CHILD_ID;
	protected static String CHILD_ID_THAT_IS_NOT_CREATED;

	public static void classSetup(String token) throws Exception {
		LEGAL_TAG_PARENT_ONE = createRandomName() + "parent";
		Thread.sleep(1);
		LEGAL_TAG_PARENT_TWO = createRandomName() + "parent";
		LEGAL_TAG_CHILD = createRandomName() + "child";
		Thread.sleep(1);
		LEGAL_TAG_CHILD_THAT_WILL_NOT_BE_CREATED = createRandomName() + "child";
		PARENT_ID_ONE = TenantUtils.getTenantName() + ":parent:" + System.currentTimeMillis();
		Thread.sleep(1);
		PARENT_ID_TWO = TenantUtils.getTenantName() + ":parent:" + System.currentTimeMillis();
		CHILD_ID = TenantUtils.getTenantName() + ":child:" + System.currentTimeMillis();
		Thread.sleep(1);
		CHILD_ID_THAT_IS_NOT_CREATED = TenantUtils.getTenantName() + ":child:" + System.currentTimeMillis();
		LegalTagUtils.create(LEGAL_TAG_PARENT_ONE, token);
		LegalTagUtils.create(LEGAL_TAG_PARENT_TWO, token);
		LegalTagUtils.create(LEGAL_TAG_CHILD, token);

		createAndAssertRecord(PARENT_ID_ONE, LEGAL_TAG_PARENT_ONE, "parent1", Lists.newArrayList("BR", "IT"), null, token);
		createAndAssertRecord(PARENT_ID_TWO, LEGAL_TAG_PARENT_TWO, "parent2", Lists.newArrayList("DE", "DK"), null, token);
	}

	public static void classTearDown(String token) throws Exception {
		purgeRecord(PARENT_ID_ONE, token);
		purgeRecord(PARENT_ID_TWO, token);
		purgeRecord(CHILD_ID, token);

		LegalTagUtils.delete(LEGAL_TAG_PARENT_ONE, token);
		LegalTagUtils.delete(LEGAL_TAG_PARENT_TWO, token);
		LegalTagUtils.delete(LEGAL_TAG_CHILD, token);
	}

	@Test
	public void should_appendOrdcAndLegalTagsWithParents_when_creatingRecordWithParentsSupplied() throws Exception {
		RecordResultMock parentRecord1 = this.retrieveRecord(PARENT_ID_ONE);
		RecordResultMock parentRecord2 = this.retrieveRecord(PARENT_ID_TWO);

		createAndAssertRecord(CHILD_ID, LEGAL_TAG_CHILD, "chiiiiiild", Lists.newArrayList("FR", "US", "CA"),
				Lists.newArrayList(PARENT_ID_ONE + ":" + parentRecord1.version,
						PARENT_ID_TWO + ":" + parentRecord2.version), testUtils.getToken());
		RecordResultMock record = this.retrieveRecord(CHILD_ID);

		assertEquals(CHILD_ID, record.id);
		assertEquals(1, record.data.size());
		assertEquals("chiiiiiild", record.data.get("name"));
		assertNotNull(record.version);
		assertEquals(KIND, record.kind);
		assertArrayEquals(new String[] { TestUtils.getAcl() }, record.acl.viewers);
		assertArrayEquals(new String[] { TestUtils.getAcl() }, record.acl.owners);
		assertEquals(3, record.legal.legaltags.length);
		assertTrue(ArrayUtils.contains(record.legal.legaltags, LEGAL_TAG_CHILD));
		assertTrue(ArrayUtils.contains(record.legal.legaltags, LEGAL_TAG_PARENT_ONE));
		assertTrue(ArrayUtils.contains(record.legal.legaltags, LEGAL_TAG_PARENT_TWO));
		assertTrue(ArrayUtils.contains(record.legal.otherRelevantDataCountries, "BR"));
		assertTrue(ArrayUtils.contains(record.legal.otherRelevantDataCountries, "IT"));
		assertTrue(ArrayUtils.contains(record.legal.otherRelevantDataCountries, "FR"));
		assertTrue(ArrayUtils.contains(record.legal.otherRelevantDataCountries, "US"));
		assertTrue(ArrayUtils.contains(record.legal.otherRelevantDataCountries, "CA"));
		assertTrue(ArrayUtils.contains(record.legal.otherRelevantDataCountries, "DE"));
		assertTrue(ArrayUtils.contains(record.legal.otherRelevantDataCountries, "DK"));
		assertEquals(2, record.ancestry.parents.length);
		assertTrue(ArrayUtils.contains(record.ancestry.parents, PARENT_ID_ONE + ":" + parentRecord1.version));
		assertTrue(ArrayUtils.contains(record.ancestry.parents, PARENT_ID_TWO + ":" + parentRecord2.version));
	}

	@Test
	public void should_returnErrorCode400_when_anInvalidChildLegalTagProvided() throws Exception {
		String childBody = createBody(CHILD_ID_THAT_IS_NOT_CREATED, "childname",
				Lists.newArrayList(LEGAL_TAG_CHILD_THAT_WILL_NOT_BE_CREATED), Lists.newArrayList("FR", "US", "CA"),
				null);

		ClientResponse response = TestUtils.send("records", "PUT",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), childBody, "");
		assertEquals(SC_BAD_REQUEST, response.getStatus());
	}

	@Test
	public void should_return400_when_noParentRecordAndNoChildLegalTagsProvided() throws Exception {
		String body = createBody(CHILD_ID_THAT_IS_NOT_CREATED, "childname", null, Lists.newArrayList("FR", "US"), null);
		ClientResponse response = TestUtils.send("records", "PUT",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "");
		assertEquals(SC_BAD_REQUEST, response.getStatus());
	}

	@Test
	public void should_returnErrorCode400_when_noParentRecordAndNoORDCValuesProvided() throws Exception {
		String body = createBody(CHILD_ID_THAT_IS_NOT_CREATED, "childname", Lists.newArrayList(LEGAL_TAG_PARENT_ONE),
				null, null);
		ClientResponse response = TestUtils.send("records", "PUT",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "");
		assertEquals(SC_BAD_REQUEST, response.getStatus());
	}

	protected RecordResultMock retrieveRecord(String recordId) throws Exception {
		System.out.println("Retrieving record=" + recordId);
		ClientResponse response = TestUtils.send("records/" + recordId, "GET",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		String responseBody = response.getEntity(String.class);
		System.out.println(" responseBody=" + responseBody);
		assertEquals(SC_OK, response.getStatus());

		return GSON.fromJson(responseBody, RecordResultMock.class);
	}

	protected static String createBody(String id, String dataValue, List<String> legalTags, List<String> ordc,
			List<String> parents) {
		JsonObject data = new JsonObject();
		data.addProperty("name", dataValue);

		JsonObject acl = new JsonObject();
		JsonArray acls = new JsonArray();
		acls.add(TestUtils.getAcl());
		acl.add("viewers", acls);
		acl.add("owners", acls);

		JsonArray tags = new JsonArray();
		if (legalTags != null) {
			legalTags.forEach(t -> tags.add(t));
		}

		JsonArray ordcJson = new JsonArray();
		if (ordc != null) {
			ordc.forEach(o -> ordcJson.add(o));
		}

		JsonObject legal = new JsonObject();
		if (legalTags != null) {
			legal.add("legaltags", tags);
		}
		legal.add("otherRelevantDataCountries", ordcJson);

		JsonObject record = new JsonObject();
		record.addProperty("id", id);
		record.addProperty("kind", KIND);
		record.add("acl", acl);
		record.add("legal", legal);
		record.add("data", data);

		if (!Collections.isEmpty(parents)) {
			JsonArray parentsJson = new JsonArray();
			parents.forEach(p -> parentsJson.add(p));

			JsonObject ancestry = new JsonObject();
			ancestry.add("parents", parentsJson);

			record.add("ancestry", ancestry);
		}

		JsonArray records = new JsonArray();
		records.add(record);

		return records.toString();
	}

	protected static void createAndAssertRecord(String parentId, String legalTagForParent, String dataValue,
			ArrayList<String> ordc, List<String> parents, String token) throws Exception {
		String parentBody = createBody(parentId, dataValue, Lists.newArrayList(legalTagForParent), ordc, parents);
		System.out.println("createAndAssertRecord");
		System.out.println("parentBody=" + parentId + " " + parentBody);
		ClientResponse response = TestUtils.send("records", "PUT",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), parentBody, "");

		String responseBody = response.getEntity(String.class);
		System.out.println("responseBody=" + parentId + " " + responseBody);
		assertEquals(SC_CREATED, response.getStatus());
		assertEquals("application/json; charset=UTF-8", response.getType().toString());

		CreateRecordResponse result = GSON.fromJson(responseBody, CreateRecordResponse.class);

		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(parentId, result.recordIds[0]);
	}

	protected static void purgeRecord(String recordId, String token) throws Exception {
		TestUtils.send("records/" + recordId, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
	}
}