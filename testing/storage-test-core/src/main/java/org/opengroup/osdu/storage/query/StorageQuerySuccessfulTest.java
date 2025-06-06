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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.model.CreatedRecordInStorage;
import org.opengroup.osdu.storage.model.GetCursorValue;
import org.opengroup.osdu.storage.util.*;

import jakarta.ws.rs.HttpMethod;

import static org.junit.Assert.assertEquals;


public abstract class StorageQuerySuccessfulTest extends TestBase {

	protected static final String RECORD = "records";
	protected static final String KIND_ONE = TenantUtils.getTenantName() + ":test:endtoend:1.1."
			+ System.currentTimeMillis();
	protected static final String KIND_ID_ONE = TenantUtils.getTenantName() + ":endtoend:1.1."
			+ System.currentTimeMillis();
	protected static final String KIND_VERSION_ID = TenantUtils.getTenantName() + ":endtoend:1.2."
			+ System.currentTimeMillis();
	protected static final String LEGAL_TAG_NAME = LegalTagUtils.createRandomName(); 

    public static void classSetup(String token) throws Exception {
        LegalTagUtils.create(LEGAL_TAG_NAME, token);
    }

    public static void classTearDown(String token) throws Exception {
        TestUtils.send(RECORD + "/" + KIND_ID_ONE, HttpMethod.DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
        TestUtils.send(RECORD + "/" + KIND_VERSION_ID, HttpMethod.DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");

    	LegalTagUtils.delete(LEGAL_TAG_NAME, token);
    }
	
	@Test
	public void should_retrieveAllKinds_when_toCursorIdIsGiven() throws Exception {
		if (configUtils != null && configUtils.getIsSchemaEndpointsEnabled()) {
			CloseableHttpResponse recordResponse = TestUtils.send("query/kinds?limit=10", HttpMethod.GET, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
					"", "");
			GetCursorValue getCursorValue = TestUtils.getResult(recordResponse, HttpStatus.SC_OK, GetCursorValue.class);
			String cursorValue = getCursorValue.getCursor();
			assertEquals(HttpStatus.SC_OK, recordResponse.getCode());
			assertEquals(cursorValue, getCursorValue.getCursor());
			CloseableHttpResponse recordResponseWithCursorValue = TestUtils.send("query/kinds?cursor=" + cursorValue + "&limit=10",
					HttpMethod.GET, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
			assertEquals(HttpStatus.SC_OK, recordResponseWithCursorValue.getCode());
		}
	}

	@Test
	public void should_retrieveAllRecords_when_kindIsGiven() throws Exception {
		CloseableHttpResponse recordResponse = createTestRecord(KIND_ONE, KIND_ID_ONE, LEGAL_TAG_NAME);
		assertEquals(HttpStatus.SC_CREATED, recordResponse.getCode());
		CloseableHttpResponse recordResponseGet = TestUtils.send("query/records?kind=" + KIND_ONE, HttpMethod.GET,
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_OK, recordResponseGet.getCode());
	}

	@Test
	public void should_queryToFetchMultipleRecords_when_recordIsGiven() throws Exception {
		CloseableHttpResponse recordResponse = createTestRecord(KIND_ONE, KIND_ID_ONE, LEGAL_TAG_NAME);
		CreatedRecordInStorage recordResult = TestUtils.getResult(recordResponse, HttpStatus.SC_CREATED,
				CreatedRecordInStorage.class);
		JsonArray recordIDS = new JsonArray();
		recordIDS.add(recordResult.recordIds[0]);
		JsonArray attribute = new JsonArray();
		attribute.add("");
		JsonObject createSearchRecordPayload = new JsonObject();
		createSearchRecordPayload.add("records", recordIDS);
		createSearchRecordPayload.add("attributes", attribute);
		String path = "query/records";
		CloseableHttpResponse recordResponsePost = TestUtils.send(path, HttpMethod.POST, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				createSearchRecordPayload.toString(), "");
		assertEquals(HttpStatus.SC_OK, recordResponsePost.getCode());
	}

	@Test
	public void should_queryToFetchMultipleRecords_when_recordIsGiven_and_trailingSlash() throws Exception {
		CloseableHttpResponse recordResponse = createTestRecord(KIND_ONE, KIND_ID_ONE, LEGAL_TAG_NAME);
		CreatedRecordInStorage recordResult = TestUtils.getResult(recordResponse, HttpStatus.SC_CREATED,
				CreatedRecordInStorage.class);
		JsonArray recordIDS = new JsonArray();
		recordIDS.add(recordResult.recordIds[0]);
		JsonArray attribute = new JsonArray();
		attribute.add("");
		JsonObject createSearchRecordPayload = new JsonObject();
		createSearchRecordPayload.add("records", recordIDS);
		createSearchRecordPayload.add("attributes", attribute);
		String path = "query/records/";
		CloseableHttpResponse recordResponsePost = TestUtils.send(path, HttpMethod.POST, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				createSearchRecordPayload.toString(), "");
		assertEquals(HttpStatus.SC_OK, recordResponsePost.getCode());
	}

	protected CloseableHttpResponse createTestRecord(String kind, String id, String legalName) throws Exception {
		String jsonInputRecord = RecordUtil.createDefaultJsonRecord(id, kind, legalName);
		return TestUtils.send(RECORD, HttpMethod.PUT, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInputRecord, "");
	}
}
