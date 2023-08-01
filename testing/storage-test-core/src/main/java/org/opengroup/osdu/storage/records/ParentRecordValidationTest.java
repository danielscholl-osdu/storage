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

import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengroup.osdu.storage.util.*;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public abstract class ParentRecordValidationTest extends TestBase {

    private static long NOW = System.currentTimeMillis();
    private static String LEGAL_TAG = LegalTagUtils.createRandomName();
    private static String KIND = TenantUtils.getFirstTenantName() + ":bulkupdate:test:1.1." + NOW;
    private static String RECORD_ID = TenantUtils.getFirstTenantName() + ":test:1.1." + NOW;
    private static String RECORD_ID_2 = TenantUtils.getFirstTenantName() + ":test:1.2." + NOW;
    private static String RECORD_ID_3 = TenantUtils.getFirstTenantName() + ":test:1.3." + NOW;


    @Before
    public void setup() throws Exception {
        LegalTagUtils.create(LEGAL_TAG, testUtils.getToken());
    }

    @After
    public void tearDown() throws Exception {
        LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());
        for (String record_id : Arrays.asList(RECORD_ID, RECORD_ID_2, RECORD_ID_3))
        {
            TestUtils.send("records/" + record_id, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        }
    }

    @Test
    public void shouldReturn200_whenRecordContainsValidAncestry() throws Exception {
        CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG), "");

        String responseString = EntityUtils.toString(response.getEntity());
        String parentIdWithVersion = JsonParser
                .parseString(responseString)
                .getAsJsonObject()
                .get("recordIdVersions")
                .getAsJsonArray()
                .get(0).getAsString();

        CloseableHttpResponse response2 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecordWithParentId(RECORD_ID_2, KIND, LEGAL_TAG, parentIdWithVersion), "");

        assertEquals(HttpStatus.SC_CREATED, response.getCode());
        assertEquals(HttpStatus.SC_CREATED, response2.getCode());
    }

    @Test
    public void shouldReturn404_whenRecordAncestryNotExisted() throws Exception {

        String parentIdWithVersion = "opendes:test:1.1.1000000000000:1000000000000000";
        CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecordWithParentId(RECORD_ID_3, KIND, LEGAL_TAG, parentIdWithVersion), "");


        String expectedErrorMessage = "The record 'RecordIdWithVersion(recordId=opendes:test:1.1.1000000000000, recordVersion=1000000000000000)' was not found";
        String actualErrorMessage = JsonParser
            .parseString(EntityUtils.toString(response.getEntity()))
            .getAsJsonObject()
            .get("message")
            .getAsString();

        assertEquals(HttpStatus.SC_NOT_FOUND, response.getCode());
        assertEquals(expectedErrorMessage, actualErrorMessage);
    }
}

