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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.DummyRecordsHelper.RecordResultMock;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class RecordWithNullFieldTest extends TestBase {

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
	}

	@AfterEach
	public void tearDown() throws Exception {
		LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());
		TestUtils.send(
				"records/" + RECORD_ID,
				"DELETE",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				"",
				""
		);
		this.testUtils = null;
	}

	@Test
	public void should_returnRecordWithoutNullFields_when_recordIsIngestedWithNullFields() throws Exception {

		// create record with null field
		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				RecordUtil.createJsonRecordWithData(RECORD_ID, KIND, LEGAL_TAG, null), "");
		assertEquals(HttpStatus.SC_CREATED, response.getCode());

		// get record
		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		JsonObject dataJson = json.get("data").getAsJsonObject();

		assertEquals("58377304471659395", dataJson.get("score-int").toString());
		assertEquals("5.837730447165939E7", dataJson.get("score-double").toString());
		assertEquals(JsonNull.INSTANCE, dataJson.get("custom"));

		// query records without attribute
		JsonArray attributes = new JsonArray();
		JsonArray records = new JsonArray();
		records.add(RECORD_ID);

		JsonObject body = new JsonObject();
		body.add("records", records);
		body.add("attributes", attributes);

		response = TestUtils.send("query/records", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body.toString(), "");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		DummyRecordsHelper.RecordsMock responseObject = new DummyRecordsHelper().getRecordsMockFromResponse(response);
		assertEquals(1, responseObject.records.length);
		assertEquals(0, responseObject.invalidRecords.length);
		assertEquals(0, responseObject.retryRecords.length);

		RecordResultMock result = responseObject.records[0];

		assertEquals(5.8377304471659392E16, result.data.get("score-int"));
		assertEquals("5.837730447165939E7", result.data.get("score-double").toString());
		assertTrue(result.data.containsKey("custom"));
		assertEquals(null, result.data.get("custom"));

		// query records with attribute
		attributes.add("data.custom");

		response = TestUtils.send("query/records", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body.toString(), "");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		responseObject = new DummyRecordsHelper().getRecordsMockFromResponse(response);
		assertEquals(1, responseObject.records.length);
		assertEquals(0, responseObject.invalidRecords.length);
		assertEquals(0, responseObject.retryRecords.length);

		result = responseObject.records[0];

		assertFalse(result.data.containsKey("score-int"));
		assertFalse(result.data.containsKey("score-double"));
		assertTrue(result.data.containsKey("custom"));
		assertEquals(null, result.data.get("custom"));
	}
}
