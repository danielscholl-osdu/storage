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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class DeleteRecordLogicallyAndItsVersionsTest extends TestBase {

	private static final Long NOW = System.currentTimeMillis();
	private static final String LEGAL_TAG = LegalTagUtils.createRandomName();

	private static final String KIND = TenantUtils.getTenantName() + ":test:endtoend:1.1."
			+ NOW;
	private static final String RECORD_ID = TenantUtils.getTenantName() + ":endtoend:1.1."
			+ NOW;

	@BeforeEach
	public void setup() throws Exception {
		this.testUtils = new TokenTestUtils();
		LegalTagUtils.create(LEGAL_TAG, testUtils.getToken());
		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				RecordUtil.createJsonRecordWithData(RECORD_ID, KIND, LEGAL_TAG, "v1"), "");
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
	}

	@AfterEach
	public void tearDown() throws Exception {
		TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());
		this.testUtils = null;
	}

	@Test
	public void should_deleteRecordAndAllVersionsLogically_when_userIsAuthorized() throws Exception {

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				RecordUtil.createJsonRecordWithData(RECORD_ID, KIND, LEGAL_TAG, "v2"), "");
		assertEquals(HttpStatus.SC_CREATED, response.getCode());

		CloseableHttpResponse versionResponse = TestUtils.send("records/versions/" + RECORD_ID, "GET",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_OK, versionResponse.getCode());

		String versions = TestUtils.getResult(versionResponse, HttpStatus.SC_OK, String.class);
		JsonObject content = new JsonParser().parse(versions).getAsJsonObject();
		JsonArray versionArray = content.get("versions").getAsJsonArray();

		String versionOne = versionArray.get(0).toString();
		String versionTwo = versionArray.get(1).toString();

		CloseableHttpResponse deleteResponse = TestUtils.send("records/", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				"{'anything':'anything'}", RECORD_ID + ":delete");

		assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.getCode());

		response = TestUtils.send("records/" + RECORD_ID + "/" + versionOne, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getCode());

		response = TestUtils.send("records/" + RECORD_ID + "/" + versionTwo, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getCode());
	}
}
