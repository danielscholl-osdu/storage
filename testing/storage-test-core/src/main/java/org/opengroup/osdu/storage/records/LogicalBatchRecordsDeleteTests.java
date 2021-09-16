// Copyright 2017-2021, Schlumberger
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

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.*;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.http.HttpStatus.SC_MULTI_STATUS;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.Assert.assertEquals;

public abstract class LogicalBatchRecordsDeleteTests extends TestBase {

    protected static final long NOW = System.currentTimeMillis();
    protected static final String KIND = TenantUtils.getTenantName() + ":delete:inttest:1.0." + NOW;
    protected static final String LEGAL_TAG = LegalTagUtils.createRandomName();
    protected static final String RECORD_ID_1 = TenantUtils.getTenantName() + ":testint:" + NOW;
    protected static final String RECORD_ID_2 = TenantUtils.getTenantName() + ":testint:" + NOW;
    private static final String NOT_EXISTED_RECORD_ID = TenantUtils.getFirstTenantName() + ":notexisted:" + NOW;

    @Test
    public void should_deleteRecordsLogically_successfully() throws Exception {
        String requestBody = String.format("[\"%s\",\"%s\"]", RECORD_ID_1, RECORD_ID_2);

        ClientResponse response = TestUtils.send("records/delete", "POST",
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), requestBody, EMPTY);
        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());

        response = TestUtils.send("records/" + RECORD_ID_1, "GET",
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());

        response = TestUtils.send("records/" + RECORD_ID_2, "GET",
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    public void should_deleteRecordsLogically_withPartialSuccess_whenOneRecordNotFound() throws Exception {
        String requestBody = String.format("[\"%s\",\"%s\",\"%s\"]", RECORD_ID_1, RECORD_ID_2, NOT_EXISTED_RECORD_ID);

        ClientResponse deleteResponse = TestUtils.send("records/delete", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                requestBody, EMPTY);

        assertEquals(SC_MULTI_STATUS, deleteResponse.getStatus());

        JsonArray jsonBody = new JsonParser().parse(deleteResponse.getEntity(String.class)).getAsJsonArray();

        assertEquals(1, jsonBody.size());
        assertEquals(getValueFromDeleteResponseJsonArray(jsonBody, "notDeletedRecordId"), NOT_EXISTED_RECORD_ID);
        assertEquals(getValueFromDeleteResponseJsonArray(jsonBody, "message"), "Record with id '" + NOT_EXISTED_RECORD_ID + "' not found");

        ClientResponse response = TestUtils.send("records/" + RECORD_ID_1, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(SC_NOT_FOUND, response.getStatus());

        response = TestUtils.send("records/" + RECORD_ID_2, "GET",HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(SC_NOT_FOUND, response.getStatus());
    }

    public void setup(String token) throws Exception {
        LegalTagUtils.create(LEGAL_TAG, token);

        String firstBody = createBody(RECORD_ID_1, "anything", Lists.newArrayList(LEGAL_TAG), Lists.newArrayList("BR", "IT"));
        String secondBody = createBody(RECORD_ID_2, "anything", Lists.newArrayList(LEGAL_TAG), Lists.newArrayList("BR", "IT"));

        ClientResponse firstResponse = TestUtils.send("records", "PUT",
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), firstBody, "");
        ClientResponse secondResponse = TestUtils.send("records", "PUT",
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), secondBody, "");

        assertEquals(HttpStatus.SC_CREATED, firstResponse.getStatus());
        assertEquals(HttpStatus.SC_CREATED, secondResponse.getStatus());
    }

    public void tearDown(String token) throws Exception {
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
        TestUtils.send("records/" + RECORD_ID_2, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");

        LegalTagUtils.delete(LEGAL_TAG, token);
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

    private String getValueFromDeleteResponseJsonArray(JsonArray jsonBody, String propertyName) {
        return jsonBody.get(0).getAsJsonObject().get(propertyName).getAsString();
    }
}