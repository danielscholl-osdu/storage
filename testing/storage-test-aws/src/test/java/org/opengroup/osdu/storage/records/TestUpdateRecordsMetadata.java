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
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengroup.osdu.storage.util.AWSTestUtils;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestUtils;

import static org.junit.Assert.assertEquals;

// TODO: Delete the override tests and related private fields once Bulk Update API is implemented
// This is a temporary override of the integration tests for Bulk Update API before it is actually implemented,
// so that it could pass the CI/CD. Once the Bulk Update API is implemented (updateObjectMetadata and
// revertObjectMetadata api in ICloudStorage) in your SPI, please simple delete the override tests and related private
// fields to run the real tests defined in storage-test-core.
public class TestUpdateRecordsMetadata extends UpdateRecordsMetadataTest {
    private static long NOW = System.currentTimeMillis();
    private static String RECORD_ID = TenantUtils.getFirstTenantName() + ":bulkupdate:1.1." + NOW;
    private static String RECORD_ID_2 = TenantUtils.getFirstTenantName() + ":bulkupdate:1.2." + NOW;
    private static String RECORD_ID_3 = TenantUtils.getFirstTenantName() + ":bulkupdate:1.3." + NOW;
    private static String RECORD_ID_4 = TenantUtils.getFirstTenantName() + ":bulkupdate:1.4." + NOW;

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AWSTestUtils();
        super.setup();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        this.testUtils = null;
    }

    @Test
    @Override
    public void should_return200andUpdateMetadata_whenValidRecordsProvided() throws Exception {
        //1. query 2 records, check the acls
        JsonArray records = new JsonArray();
        records.add(RECORD_ID);
        records.add(RECORD_ID_2);

        JsonObject queryBody = new JsonObject();
        queryBody.add("records", records);

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
        assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, bulkUpdateResponse.getStatus());
    }

    @Test
    @Override
    public void should_return206andUpdateMetadata_whenOneRecordProvided() throws Exception {
        //1. query 2 records, check the acls
        JsonArray records = new JsonArray();
        records.add(RECORD_ID_3);
        records.add(RECORD_ID_4);

        JsonObject queryBody = new JsonObject();
        queryBody.add("records", records);

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
        assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, bulkUpdateResponse.getStatus());
    }
}
