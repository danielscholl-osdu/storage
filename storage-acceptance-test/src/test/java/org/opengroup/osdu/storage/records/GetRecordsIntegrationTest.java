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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class GetRecordsIntegrationTest extends TestBase {
	private static final String RECORD_ID = TenantUtils.getTenantName() + ":getrecord:" + System.currentTimeMillis();
	private static final String ANOTHER_RECORD_ID = TenantUtils.getTenantName() + ":getrecordnodup:" + System.currentTimeMillis();

	private static final String KIND = TenantUtils.getTenantName() + ":ds:getrecord:1.0."
			+ System.currentTimeMillis();

	private static String LEGAL_TAG_NAME_A;
	private static String LEGAL_TAG_NAME_B;

	private static final TokenTestUtils TOKEN_TEST_UTILS = new TokenTestUtils();

	@BeforeAll
	public static void classSetup() throws Exception {
		GetRecordsIntegrationTest.classSetup(TOKEN_TEST_UTILS.getToken());
	}

	@AfterAll
	public static void classTearDown() throws Exception {
		GetRecordsIntegrationTest.classTearDown(TOKEN_TEST_UTILS.getToken());
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
        LEGAL_TAG_NAME_A = LegalTagUtils.createRandomName();
        Thread.sleep(100);
        LEGAL_TAG_NAME_B = LegalTagUtils.createRandomName();

        LegalTagUtils.create(LEGAL_TAG_NAME_A, token);
        LegalTagUtils.create(LEGAL_TAG_NAME_B, token);

		String jsonInput = RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG_NAME_A);

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput, "");
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
		assertTrue(response.getEntity().getContentType().contains("application/json"));
	}

	public static void classTearDown(String token) throws Exception {
		TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
        LegalTagUtils.delete(LEGAL_TAG_NAME_A, token);
        Thread.sleep(100);
        LegalTagUtils.delete(LEGAL_TAG_NAME_B, token);
	}

	@Test
	public void should_getRecord_when_validRecordIdIsProvided() throws Exception {
		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		JsonObject dataJson = json.get("data").getAsJsonObject();
		JsonObject acl = json.get("acl").getAsJsonObject();

		assertEquals(RECORD_ID, json.get("id").getAsString());
		assertEquals(KIND, json.get("kind").getAsString());
		assertEquals(TestUtils.getAcl(), acl.get("owners").getAsString());
		assertEquals(TestUtils.getAcl(), acl.get("viewers").getAsString());

		assertEquals("58377304471659395", dataJson.get("int-tag").getAsJsonObject().get("score-int").toString());
		assertEquals("5.837730447165939E7",
				dataJson.get("double-tag").getAsJsonObject().get("score-double").toString());
		assertEquals("123456789", dataJson.get("count").toString());
	}

	@Test
	public void should_getRecord_withoutDuplicates_when_duplicateAclAndLegaltagsAreProvided() throws Exception {
		String jsonInputWithDuplicates = RecordUtil.createRecordWithDuplicateAclAndLegaltags(ANOTHER_RECORD_ID, KIND, LEGAL_TAG_NAME_A);
		CloseableHttpResponse putResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInputWithDuplicates, "");
		assertEquals(HttpStatus.SC_CREATED, putResponse.getCode());
		assertTrue(putResponse.getEntity().getContentType().contains("application/json"));

		CloseableHttpResponse response = TestUtils.send("records/" + ANOTHER_RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		JsonObject acl = json.get("acl").getAsJsonObject();
		JsonObject legal = json.get("legal").getAsJsonObject();

		assertEquals(ANOTHER_RECORD_ID, json.get("id").getAsString());
		assertEquals(KIND, json.get("kind").getAsString());
		assertEquals(LEGAL_TAG_NAME_A, legal.get("legaltags").getAsString());
		assertEquals(TestUtils.getAcl(), acl.get("owners").getAsString());
		assertEquals(TestUtils.getAcl(), acl.get("viewers").getAsString());
	}

	@Test
	public void should_getOnlyTheCertainDataFields_when_attributesAreProvided() throws Exception {
		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"?attribute=data.count&attribute=data.int-tag.score-int");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		JsonObject dataJson = json.get("data").getAsJsonObject();
		JsonObject acl = json.get("acl").getAsJsonObject();

		assertEquals(RECORD_ID, json.get("id").getAsString());
		assertEquals(KIND, json.get("kind").getAsString());
		assertEquals(TestUtils.getAcl(), acl.get("owners").getAsString());
		assertEquals(TestUtils.getAcl(), acl.get("viewers").getAsString());

		assertEquals("58377304471659395", dataJson.get("int-tag.score-int").getAsString());
		assertNull(dataJson.get("double-tag"));
		assertEquals("123456789", dataJson.get("count").toString());
	}

	@Test
	public void should_notReturnFieldsAlreadyInDatastore_when_returningRecord() throws Exception {
		CloseableHttpResponse response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_OK, response.getCode());

		JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();

		assertNotNull(json.get("id"));
		assertNotNull(json.get("kind"));
		assertNotNull(json.get("acl"));
		assertNotNull(json.get("version"));
		assertNotNull(json.get("data"));
		assertNotNull(json.get("createTime"));

		assertNull(json.get("bucket"));
		assertNull(json.get("status"));
		assertNull(json.get("modifyUser"));
		assertNull(json.get("modifyTime"));
	}

    @Test
    public void should_legaltagChange_when_updateRecordWithLegaltag() throws Exception {
		String newJsonInput = RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG_NAME_B);
        CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), newJsonInput, "?skipdupes=false");
        assertEquals(HttpStatus.SC_CREATED, response.getCode());

        response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        JsonObject json = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();

        assertEquals(RECORD_ID, json.get("id").getAsString());
        assertEquals(KIND, json.get("kind").getAsString());

        JsonArray legaltags = json.get("legal").getAsJsonObject().get("legaltags").getAsJsonArray();
        String updatedLegaltag = legaltags.get(0).getAsString();
        assertEquals(1, legaltags.size());
        assertEquals(LEGAL_TAG_NAME_B, updatedLegaltag);
    }
}
