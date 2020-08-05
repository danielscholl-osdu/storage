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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.*;
import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public abstract class UpdateRecordsMetadataTest extends TestBase {
    private static long NOW = System.currentTimeMillis();
    private static String LEGAL_TAG = LegalTagUtils.createRandomName();
    private static String KIND = TenantUtils.getFirstTenantName() + ":bulkupdate:test:1.1." + NOW;
    private static String RECORD_ID = TenantUtils.getFirstTenantName() + ":bulkupdate:1.1." + NOW;
    private static String RECORD_ID_2 = TenantUtils.getFirstTenantName() + ":bulkupdate:1.2." + NOW;
    private static String RECORD_ID_3 = TenantUtils.getFirstTenantName() + ":bulkupdate:1.3." + NOW;
    private static String RECORD_ID_4 = TenantUtils.getFirstTenantName() + ":bulkupdate:1.4." + NOW;
    private static final DummyRecordsHelper RECORDS_HELPER = new DummyRecordsHelper();


    @Before
    public void setup() throws Exception {
        LegalTagUtils.create(LEGAL_TAG, testUtils.getToken());
        ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");
        ClientResponse response2 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID_2, KIND, LEGAL_TAG), "");
        ClientResponse response3 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID_3, KIND, LEGAL_TAG), "");
        ClientResponse response4 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID_4, KIND, LEGAL_TAG), "");
        assertEquals(HttpStatus.SC_CREATED, response.getStatus());
        assertEquals(HttpStatus.SC_CREATED, response2.getStatus());
        assertEquals(HttpStatus.SC_CREATED, response3.getStatus());
        assertEquals(HttpStatus.SC_CREATED, response4.getStatus());

    }

    @After
    public void tearDown() throws Exception {
        LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());
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
}
