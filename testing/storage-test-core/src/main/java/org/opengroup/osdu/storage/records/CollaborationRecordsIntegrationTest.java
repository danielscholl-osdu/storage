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

import com.sun.jersey.api.client.ClientResponse;
import org.junit.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class CollaborationRecordsIntegrationTest extends TestBase {
    protected static final String TENANT_NAME = TenantUtils.getTenantName();
    protected static final long CURRENT_TIME_MILLIS = System.currentTimeMillis();
    protected static final String COLLABORATION1_ID = "collaboration1";
    protected static final String COLLABORATION2_ID = "collaboration2";
    protected static final String RECORD_ID_1 = TENANT_NAME + ":inttest:1" + CURRENT_TIME_MILLIS;
    protected static final String RECORD_ID_2 = TENANT_NAME + ":inttest:2" + CURRENT_TIME_MILLIS;
    protected static final String RECORD_ID_3 = TENANT_NAME + ":inttest:3" + CURRENT_TIME_MILLIS;
    protected static final String KIND1 = TENANT_NAME + ":ds:inttest:1" + CURRENT_TIME_MILLIS;
    protected static final String KIND2 = TENANT_NAME + ":ds:inttest:2" + CURRENT_TIME_MILLIS;

    protected static String LEGAL_TAG_NAME_A;

    public static void classSetup(String token) throws Exception {
        LEGAL_TAG_NAME_A = LegalTagUtils.createRandomName();
        LegalTagUtils.create(LEGAL_TAG_NAME_A, token);

        createRecord(RECORD_ID_1, KIND1, token); //v1
        createRecord(RECORD_ID_1 + COLLABORATION1_ID, KIND1, token); //v2
        createRecord(RECORD_ID_1 + COLLABORATION1_ID, KIND1, token); //v3
        createRecord(RECORD_ID_1 + COLLABORATION2_ID, KIND1, token); //v4

        createRecord(RECORD_ID_2, KIND1, token); //v1
        createRecord(RECORD_ID_2 + COLLABORATION2_ID, KIND1, token); //v2

        createRecord(RECORD_ID_3 + COLLABORATION1_ID, KIND2, token); //v1
        createRecord(RECORD_ID_3 + COLLABORATION2_ID, KIND2, token); //v2
    }

    public static void classTearDown(String token) throws Exception {
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", HeaderUtils.getHeaders(TENANT_NAME, token), "", "");
        TestUtils.send("records/" + RECORD_ID_1 + COLLABORATION1_ID, "DELETE", HeaderUtils.getHeaders(TENANT_NAME, token), "", "");
        TestUtils.send("records/" + RECORD_ID_1 + COLLABORATION2_ID, "DELETE", HeaderUtils.getHeaders(TENANT_NAME, token), "", "");
        TestUtils.send("records/" + RECORD_ID_2, "DELETE", HeaderUtils.getHeaders(TENANT_NAME, token), "", "");
        TestUtils.send("records/" + RECORD_ID_2 + COLLABORATION2_ID, "DELETE", HeaderUtils.getHeaders(TENANT_NAME, token), "", "");
        TestUtils.send("records/" + RECORD_ID_3 + COLLABORATION1_ID, "DELETE", HeaderUtils.getHeaders(TENANT_NAME, token), "", "");
        TestUtils.send("records/" + RECORD_ID_3 + COLLABORATION2_ID, "DELETE", HeaderUtils.getHeaders(TENANT_NAME, token), "", "");
        LegalTagUtils.delete(LEGAL_TAG_NAME_A, token);
    }

    @Test
    public void should_test_smth() throws Exception {
    }

    private static void createRecord(String recordId, String kind, String token) throws Exception {
        String jsonInput = RecordUtil.createDefaultJsonRecord(recordId, kind, LEGAL_TAG_NAME_A);
        ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TENANT_NAME, token), jsonInput, "");
        assertEquals(201, response.getStatus());
        assertTrue(response.getType().toString().contains("application/json"));
    }
}
