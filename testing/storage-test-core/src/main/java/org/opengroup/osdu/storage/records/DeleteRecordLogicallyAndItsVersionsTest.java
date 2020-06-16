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

import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;

public abstract class DeleteRecordLogicallyAndItsVersionsTest extends TestBase {

	protected static final Long NOW = System.currentTimeMillis();
	protected static final String LEGAL_TAG = LegalTagUtils.createRandomName();

	protected static final String KIND = TenantUtils.getTenantName() + ":test:endtoend:1.1."
			+ NOW;
	protected static final String RECORD_ID = TenantUtils.getTenantName() + ":testendtoend:1.1."
			+ NOW;

	@Before
	public void setup() throws Exception {

		LegalTagUtils.create(LEGAL_TAG, testUtils.getToken());

		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				RecordUtil.createJsonRecordWithData(RECORD_ID, KIND, LEGAL_TAG, "v1"), "");
		assertEquals(HttpStatus.SC_CREATED, response.getStatus());
	}

	@After
	public void tearDown() throws Exception {
		TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");

		LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());
	}

	@Test
	public void should_deleteRecordAndAllVersionsLogically_when_userIsAuthorized() throws Exception {

		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				RecordUtil.createJsonRecordWithData(RECORD_ID, KIND, LEGAL_TAG, "v2"), "");
		assertEquals(HttpStatus.SC_CREATED, response.getStatus());

		ClientResponse versionResponse = TestUtils.send("records/versions/" + RECORD_ID, "GET",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_OK, versionResponse.getStatus());

		String versions = TestUtils.getResult(versionResponse, HttpStatus.SC_OK, String.class);
		JsonObject content = new JsonParser().parse(versions).getAsJsonObject();
		JsonArray versionArray = content.get("versions").getAsJsonArray();

		String versionOne = versionArray.get(0).toString();
		String versionTwo = versionArray.get(1).toString();

		ClientResponse deleteResponse = TestUtils.send("records/", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				"{'anything':'anything'}", RECORD_ID + ":delete");

		assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.getStatus());

		response = TestUtils.send("records/" + RECORD_ID + "/" + versionOne, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());

		response = TestUtils.send("records/" + RECORD_ID + "/" + versionTwo, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
	}
}