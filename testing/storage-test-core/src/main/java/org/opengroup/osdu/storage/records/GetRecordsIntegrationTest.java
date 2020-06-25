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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonArray;
import org.apache.http.HttpStatus;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;

public abstract class GetRecordsIntegrationTest extends TestBase {
	protected static final String RECORD_ID = TenantUtils.getTenantName() + ":id:" + System.currentTimeMillis();

	protected static final String KIND = TenantUtils.getTenantName() + ":ds:getrecord:1.0."
			+ System.currentTimeMillis();

    protected static String LEGAL_TAG_NAME_A;
    protected static String LEGAL_TAG_NAME_B;

	public static void classSetup(String token) throws Exception {
        LEGAL_TAG_NAME_A = LegalTagUtils.createRandomName();
        Thread.sleep(100);
        LEGAL_TAG_NAME_B = LegalTagUtils.createRandomName();

        LegalTagUtils.create(LEGAL_TAG_NAME_A, token);
        LegalTagUtils.create(LEGAL_TAG_NAME_B, token);

		String jsonInput = RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG_NAME_A);


        ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput, "");
		assertEquals(201, response.getStatus());
		assertEquals("application/json; charset=UTF-8", response.getType().toString());
	}

	public static void classTearDown(String token) throws Exception {
		TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
        LegalTagUtils.delete(LEGAL_TAG_NAME_A, token);
        Thread.sleep(100);
        LegalTagUtils.delete(LEGAL_TAG_NAME_B, token);
	}

	@Test
	public void should_getRecord_when_validRecordIdIsProvided() throws Exception {
		ClientResponse response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_OK, response.getStatus());

		JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
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
	public void should_getOnlyTheCertainDataFields_when_attributesAreProvided() throws Exception {
		ClientResponse response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"?attribute=data.count&attribute=data.int-tag.score-int");
		assertEquals(HttpStatus.SC_OK, response.getStatus());

		JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
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
		ClientResponse response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_OK, response.getStatus());

		JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();

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
        ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), newJsonInput, "?skipdupes=false");
        assertEquals(201, response.getStatus());

        response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();

        assertEquals(RECORD_ID, json.get("id").getAsString());
        assertEquals(KIND, json.get("kind").getAsString());

        JsonArray legaltags = json.get("legal").getAsJsonObject().get("legaltags").getAsJsonArray();
        String updatedLegaltag = legaltags.get(0).getAsString();
        assertEquals(1, legaltags.size());
        assertEquals(LEGAL_TAG_NAME_B, updatedLegaltag);
    }
}