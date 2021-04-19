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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.*;
import org.opengroup.osdu.storage.util.*;

import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;

import java.util.Map;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.http.HttpStatus.*;
import static org.junit.Assert.*;

public abstract class UpdateRecordsMetadataTest extends TestBase {
    private static final String TAG_KEY = "tagkey1";
    private static final String TAG_VALUE1 = "tagvalue1";
    private static final String TAG_VALUE2 = "tagvalue2";

    private static long NOW = System.currentTimeMillis();
    private static String ACL = TestUtils.getIntegrationTesterAcl();
    private static String ACL_2 = TestUtils.getAcl();
    private static String LEGAL_TAG = LegalTagUtils.createRandomName();
    private static String LEGAL_TAG_2 = LegalTagUtils.createRandomName() + "2";
    private static String LEGAL_TAG_3 = LegalTagUtils.createRandomName() + "3";
    private static String LEGAL_TAG_4 = LegalTagUtils.createRandomName() + "4";
    private static String KIND = TenantUtils.getFirstTenantName() + ":bulkupdate:test:1.1." + NOW;
    private static String RECORD_ID = TenantUtils.getFirstTenantName() + ":test:1.1." + NOW;
    private static String RECORD_ID_2 = TenantUtils.getFirstTenantName() + ":test:1.2." + NOW;
    private static String RECORD_ID_3 = TenantUtils.getFirstTenantName() + ":test:1.3." + NOW;
    private static String RECORD_ID_4 = TenantUtils.getFirstTenantName() + ":test:1.4." + NOW;
    private static String NOT_EXISTED_RECORD_ID = TenantUtils.getFirstTenantName() + ":bulkupdate:1.6." + NOW;
    private static final DummyRecordsHelper RECORDS_HELPER = new DummyRecordsHelper();


    @Before
    public void setup() throws Exception {
        LegalTagUtils.create(LEGAL_TAG, testUtils.getToken());
        LegalTagUtils.create(LEGAL_TAG_2, testUtils.getToken());
        LegalTagUtils.create(LEGAL_TAG_3, testUtils.getToken());
        LegalTagUtils.create(LEGAL_TAG_4, testUtils.getToken());
        ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");
        ClientResponse response2 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID_2, KIND, LEGAL_TAG_2), "");
        ClientResponse response3 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID_3, KIND, LEGAL_TAG_3), "");
        ClientResponse response4 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID_4, KIND, LEGAL_TAG_4), "");
        assertEquals(HttpStatus.SC_CREATED, response.getStatus());
        assertEquals(HttpStatus.SC_CREATED, response2.getStatus());
        assertEquals(HttpStatus.SC_CREATED, response3.getStatus());
        assertEquals(HttpStatus.SC_CREATED, response4.getStatus());

    }

    @After
    public void tearDown() throws Exception {
        LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());
        LegalTagUtils.delete(LEGAL_TAG_2, testUtils.getToken());
        LegalTagUtils.delete(LEGAL_TAG_3, testUtils.getToken());
        LegalTagUtils.delete(LEGAL_TAG_4, testUtils.getToken());
        TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_2, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_3, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_4, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    }

    @Test
    public void should_return200andUpdateMetadata_whenValidRecordsProvided() throws Exception {
        //1. query 2 records, check the acls
        JsonArray records = new JsonArray();
        records.add(RECORD_ID);
        records.add(RECORD_ID_2);

        JsonObject queryBody = new JsonObject();
        queryBody.add("records", records);

        Map<String, String> queryHeader = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        queryHeader.put("frame-of-reference", "none");
        ClientResponse queryResponse1 = TestUtils.send("query/records:batch", "POST", queryHeader, queryBody.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, queryResponse1.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject1 = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse1);
        assertEquals(2, queryResponseObject1.records.length);
        assertEquals(TestUtils.getAcl(), queryResponseObject1.records[0].acl.viewers[0]);
        assertEquals(TestUtils.getAcl(), queryResponseObject1.records[0].acl.owners[0]);

        //2. bulk update requests, change acls
        JsonArray value = new JsonArray();
        value.add(TestUtils.getIntegrationTesterAcl());
        JsonObject op1 = new JsonObject();
        op1.addProperty("op", "replace");
        op1.addProperty("path", "/acl/viewers");
        op1.add("value", value);
        JsonObject op2 = new JsonObject();
        op2.addProperty("op", "replace");
        op2.addProperty("path", "/acl/owners");
        op2.add("value", value);
        JsonArray ops = new JsonArray();
        ops.add(op1);
        ops.add(op2);

        JsonObject query = new JsonObject();
        query.add("ids", records);

        JsonObject updateBody = new JsonObject();
        updateBody.add("query", query);
        updateBody.add("ops", ops);

        ClientResponse bulkUpdateResponse = TestUtils.send("records", "PATCH", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), updateBody.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, bulkUpdateResponse.getStatus());

        //3. query 2 records again, check acls
        ClientResponse queryResponse2 = TestUtils.send("query/records:batch", "POST", queryHeader, queryBody.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, queryResponse2.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject2 = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse2);
        assertEquals(2, queryResponseObject2.records.length);
        assertEquals(TestUtils.getIntegrationTesterAcl(), queryResponseObject2.records[0].acl.viewers[0]);
        assertEquals(TestUtils.getIntegrationTesterAcl(), queryResponseObject2.records[0].acl.owners[0]);
    }

    @Test
    public void should_return206andUpdateMetadata_whenOneRecordProvided() throws Exception {
        //1. query 2 records, check the acls
        JsonArray records = new JsonArray();
        records.add(RECORD_ID_3);
        records.add(RECORD_ID_4);

        JsonObject queryBody = new JsonObject();
        queryBody.add("records", records);

        Map<String, String> queryHeader = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        queryHeader.put("slb-frame-of-reference", "none");
        ClientResponse queryResponse1 = TestUtils.send("query/records:batch", "POST", queryHeader, queryBody.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, queryResponse1.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject1 = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse1);
        assertEquals(2, queryResponseObject1.records.length);
        assertEquals(TestUtils.getAcl(), queryResponseObject1.records[0].acl.viewers[0]);
        assertEquals(TestUtils.getAcl(), queryResponseObject1.records[0].acl.owners[0]);

        //2. bulk update requests, change acls
        JsonArray value = new JsonArray();
        value.add(TestUtils.getIntegrationTesterAcl());
        JsonObject op1 = new JsonObject();
        op1.addProperty("op", "replace");
        op1.addProperty("path", "/acl/viewers");
        op1.add("value", value);
        JsonObject op2 = new JsonObject();
        op2.addProperty("op", "replace");
        op2.addProperty("path", "/acl/owners");
        op2.add("value", value);
        JsonArray ops = new JsonArray();
        ops.add(op1);
        ops.add(op2);

        JsonArray records2 = new JsonArray();
        records2.add(RECORD_ID_3);
        records2.add(RECORD_ID_4);
        records2.add(TenantUtils.getFirstTenantName() + ":not:found");
        JsonObject query = new JsonObject();
        query.add("ids", records2);

        JsonObject updateBody = new JsonObject();
        updateBody.add("query", query);
        updateBody.add("ops", ops);

        ClientResponse bulkUpdateResponse = TestUtils.send("records", "PATCH", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), updateBody.toString(),
                "");
        assertEquals(HttpStatus.SC_PARTIAL_CONTENT, bulkUpdateResponse.getStatus());

        //3. query 2 records again, check acls
        ClientResponse queryResponse2 = TestUtils.send("query/records:batch", "POST", queryHeader, queryBody.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, queryResponse2.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject2 = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse2);
        assertEquals(2, queryResponseObject2.records.length);
        assertEquals(TestUtils.getIntegrationTesterAcl(), queryResponseObject2.records[0].acl.viewers[0]);
        assertEquals(TestUtils.getIntegrationTesterAcl(), queryResponseObject2.records[0].acl.owners[0]);
    }

    @Test
    public void should_return200AndUpdateTagsMetadata_whenValidRecordsProvided() throws Exception {
        //add operation
        JsonObject updateBody = buildUpdateTagBody(RECORD_ID, "add", TAG_KEY + ":" + TAG_VALUE1);

        ClientResponse updateResponse = sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());
        ClientResponse recordResponse = sendRequest("GET", "records/" + RECORD_ID, EMPTY, testUtils.getToken());

        assertEquals(SC_OK, updateResponse.getStatus());
        assertEquals(SC_OK, recordResponse.getStatus());

        JsonObject resultObject = bodyToJsonObject(updateResponse.getEntity(String.class));
        assertEquals(RECORD_ID, resultObject.get("recordIds").getAsJsonArray().get(0).getAsString());

        resultObject = bodyToJsonObject(recordResponse.getEntity(String.class));
        assertEquals(TAG_VALUE1, resultObject.get("tags").getAsJsonObject().get(TAG_KEY).getAsString());

        //replace operation
        updateBody = buildUpdateTagBody(RECORD_ID, "replace", TAG_KEY + ":" + TAG_VALUE2);
        sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());
        recordResponse = sendRequest("GET", "records/" + RECORD_ID, EMPTY, testUtils.getToken());

        resultObject = bodyToJsonObject(recordResponse.getEntity(String.class));
        assertEquals(TAG_VALUE2, resultObject.get("tags").getAsJsonObject().get(TAG_KEY).getAsString());

        //remove operation
        updateBody = buildUpdateTagBody(RECORD_ID,"remove", TAG_KEY);
        sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());
        recordResponse = sendRequest("GET", "records/" + RECORD_ID, EMPTY, testUtils.getToken());

        resultObject = new JsonParser().parse(recordResponse.getEntity(String.class)).getAsJsonObject();
        assertNull(resultObject.get("tags"));
    }

    @Test
    public void should_return206andUpdateTagsMetadata_whenNotExistedRecordProvided() throws Exception {
        JsonObject updateBody = buildUpdateTagBody(NOT_EXISTED_RECORD_ID, "replace", TAG_KEY + ":" + TAG_VALUE1);

        ClientResponse updateResponse = sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());

        assertEquals(SC_PARTIAL_CONTENT, updateResponse.getStatus());
        JsonObject resultObject = bodyToJsonObject(updateResponse.getEntity(String.class));

        System.out.println(resultObject.toString());
        assertEquals(NOT_EXISTED_RECORD_ID, resultObject.get("notFoundRecordIds").getAsJsonArray().getAsString());
    }

    private static ClientResponse sendRequest(String method, String path, String body, String token) throws Exception {
        return TestUtils
            .send(path, method, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), body, "");
    }

    private String toJson(Object object) {
        return new Gson().toJson(object);
    }

    private JsonObject bodyToJsonObject(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }


    private JsonObject buildUpdateTagBody(String id, String op, String val) {
        JsonArray records = new JsonArray();
        records.add(id);

        JsonArray value = new JsonArray();
        value.add(val);
        JsonObject operation = new JsonObject();
        operation.addProperty("op", op);
        operation.addProperty("path", "/tags");
        operation.add("value", value);
        JsonArray ops = new JsonArray();
        ops.add(operation);

        JsonObject query = new JsonObject();
        query.add("ids", records);

        JsonObject updateBody = new JsonObject();
        updateBody.add("query", query);
        updateBody.add("ops", ops);

        return updateBody;
    }

    @Test
    public void should_return200AndUpdateLegalMetadataOr400ForRemoveRestriction_whenValidRecordsProvided() throws Exception {
        //add operation
        JsonObject updateBody = buildUpdateLegalBody(RECORD_ID, "add", LEGAL_TAG);

        ClientResponse updateResponse = sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());
        ClientResponse recordResponse = sendRequest("GET", "records/" + RECORD_ID, EMPTY, testUtils.getToken());

        assertEquals(SC_OK, updateResponse.getStatus());
        assertEquals(SC_OK, recordResponse.getStatus());

        JsonObject resultObject = bodyToJsonObject(updateResponse.getEntity(String.class));
        assertEquals(RECORD_ID, resultObject.get("recordIds").getAsJsonArray().get(0).getAsString());

        resultObject = bodyToJsonObject(recordResponse.getEntity(String.class));
        assertEquals(LEGAL_TAG, resultObject.get("legal").getAsJsonObject().get("legaltags").getAsString());

        //replace operation
        updateBody = buildUpdateLegalBody(RECORD_ID, "replace", LEGAL_TAG_2);
        sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());
        recordResponse = sendRequest("GET", "records/" + RECORD_ID, EMPTY, testUtils.getToken());

        resultObject = bodyToJsonObject(recordResponse.getEntity(String.class));
        assertEquals(LEGAL_TAG_2, resultObject.get("legal").getAsJsonObject().get("legaltags").getAsString());

        //remove operation
        updateBody = buildUpdateLegalBody(RECORD_ID,"remove", LEGAL_TAG_2);
        updateResponse= sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());

        assertEquals(SC_BAD_REQUEST,updateResponse.getStatus());
    }

    @Test
    public void should_return200AndUpdateAclViewersMetadataOr400ForRemoveRestriction_whenValidRecordsProvided() throws Exception {
        //add operation
        JsonObject updateBody = buildUpdateAclBody(RECORD_ID, "add","/acl/viewers", ACL);

        ClientResponse updateResponse = sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());
        ClientResponse recordResponse = sendRequest("GET", "records/" + RECORD_ID, EMPTY, testUtils.getToken());

        assertEquals(SC_OK, updateResponse.getStatus());
        assertEquals(SC_OK, recordResponse.getStatus());

        JsonObject resultObject = bodyToJsonObject(updateResponse.getEntity(String.class));
        assertEquals(RECORD_ID, resultObject.get("recordIds").getAsJsonArray().get(0).getAsString());

        resultObject = bodyToJsonObject(recordResponse.getEntity(String.class));
        assertEquals(ACL, resultObject.get("acl").getAsJsonObject().get("viewers").getAsJsonArray().get(0).getAsString());

        //replace operation
        updateBody = buildUpdateAclBody(RECORD_ID, "replace","/acl/viewers", ACL_2);
        sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());
        recordResponse = sendRequest("GET", "records/" + RECORD_ID, EMPTY, testUtils.getToken());

        resultObject = bodyToJsonObject(recordResponse.getEntity(String.class));
        assertEquals(ACL_2, resultObject.get("acl").getAsJsonObject().get("viewers").getAsJsonArray().get(0).getAsString());

        //remove operation
        updateBody = buildUpdateAclBody(RECORD_ID,"remove","/acl/viewers", ACL_2);
        updateResponse = sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());

        assertEquals(SC_BAD_REQUEST,updateResponse.getStatus());
    }

    @Test
    public void should_return200AndUpdateAclOwnersMetadataOr400ForRemoveRestriction_whenValidRecordsProvided() throws Exception {
        //add operation
        JsonObject updateBody = buildUpdateAclBody(RECORD_ID, "add","/acl/owners", ACL);

        ClientResponse updateResponse = sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());
        ClientResponse recordResponse = sendRequest("GET", "records/" + RECORD_ID, EMPTY, testUtils.getToken());

        assertEquals(SC_OK, updateResponse.getStatus());
        assertEquals(SC_OK, recordResponse.getStatus());

        JsonObject resultObject = bodyToJsonObject(updateResponse.getEntity(String.class));
        assertEquals(RECORD_ID, resultObject.get("recordIds").getAsJsonArray().get(0).getAsString());

        resultObject = bodyToJsonObject(recordResponse.getEntity(String.class));
        assertEquals(ACL, resultObject.get("acl").getAsJsonObject().get("owners").getAsJsonArray().get(0).getAsString());

        //remove operation
        updateBody = buildUpdateAclBody(RECORD_ID,"remove","/acl/owners", ACL_2);
        sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());

        recordResponse = sendRequest("GET", "records/" + RECORD_ID, EMPTY, testUtils.getToken());
        resultObject = new JsonParser().parse(recordResponse.getEntity(String.class)).getAsJsonObject();

        assertEquals(ACL, resultObject.get("acl").getAsJsonObject().get("owners").getAsJsonArray().get(0).getAsString());

        //replace operation
        updateBody = buildUpdateAclBody(RECORD_ID, "replace","/acl/owners ", ACL);
        sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());
        recordResponse = sendRequest("GET", "records/" + RECORD_ID, EMPTY, testUtils.getToken());

        resultObject = bodyToJsonObject(recordResponse.getEntity(String.class));
        assertEquals(ACL, resultObject.get("acl").getAsJsonObject().get("owners").getAsJsonArray().get(0).getAsString());
    }

    @Test
    public void should_return206andUpdateLegalMetadata_whenNotExistedRecordProvided() throws Exception {
        JsonObject updateBody = buildUpdateLegalBody(NOT_EXISTED_RECORD_ID, "replace", LEGAL_TAG);

        ClientResponse updateResponse = sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());

        assertEquals(SC_PARTIAL_CONTENT, updateResponse.getStatus());
        JsonObject resultObject = bodyToJsonObject(updateResponse.getEntity(String.class));

        System.out.println(resultObject.toString());
        assertEquals(NOT_EXISTED_RECORD_ID, resultObject.get("notFoundRecordIds").getAsJsonArray().getAsString());
    }

    @Test
    public void should_return206andUpdateAclMetadata_whenNotExistedRecordProvided() throws Exception {
        JsonObject updateBody = buildUpdateAclBody(NOT_EXISTED_RECORD_ID, "replace","/acl/viewers", ACL);

        ClientResponse updateResponse = sendRequest("PATCH", "records", toJson(updateBody), testUtils.getToken());

        assertEquals(SC_PARTIAL_CONTENT, updateResponse.getStatus());
        JsonObject resultObject = bodyToJsonObject(updateResponse.getEntity(String.class));

        System.out.println(resultObject.toString());
        assertEquals(NOT_EXISTED_RECORD_ID, resultObject.get("notFoundRecordIds").getAsJsonArray().getAsString());
    }

    private JsonObject buildUpdateLegalBody(String id, String op, String val) {
        JsonArray records = new JsonArray();
        records.add(id);

        JsonArray value = new JsonArray();
        value.add(val);
        JsonObject operation = new JsonObject();
        operation.addProperty("op", op);
        operation.addProperty("path", "/legal/legaltags");
        operation.add("value", value);
        JsonArray ops = new JsonArray();
        ops.add(operation);

        JsonObject query = new JsonObject();
        query.add("ids", records);

        JsonObject updateBody = new JsonObject();
        updateBody.add("query", query);
        updateBody.add("ops", ops);

        return updateBody;
    }

    private JsonObject buildUpdateAclBody(String id, String op, String path, String val) {
        JsonArray records = new JsonArray();
        records.add(id);

        JsonArray value = new JsonArray();
        value.add(val);
        JsonObject operation = new JsonObject();
        operation.addProperty("op", op);
        operation.addProperty("path", path);
        operation.add("value", value);
        JsonArray ops = new JsonArray();
        ops.add(operation);

        JsonObject query = new JsonObject();
        query.add("ids", records);

        JsonObject updateBody = new JsonObject();
        updateBody.add("query", query);
        updateBody.add("ops", ops);

        return updateBody;
    }
}
