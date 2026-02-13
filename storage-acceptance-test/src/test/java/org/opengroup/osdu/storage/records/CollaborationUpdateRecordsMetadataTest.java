/*
 *  Copyright 2020-2024 Google LLC
 *  Copyright 2020-2024 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.records;

import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengroup.osdu.storage.records.CollaborationRecordsPurgeTest.COLLABORATION1_ID;
import static org.opengroup.osdu.storage.records.UpdateRecordsMetadataTest.TAG_KEY;
import static org.opengroup.osdu.storage.records.UpdateRecordsMetadataTest.TAG_VALUE1;
import static org.opengroup.osdu.storage.util.HeaderUtils.getHeadersWithxCollaboration;
import static org.opengroup.osdu.storage.util.TestUtils.assertRecordVersionAndReturnResponseBody;
import static org.opengroup.osdu.storage.util.TestUtils.createRecordInCollaborationContext_AndReturnVersion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.ConfigUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class CollaborationUpdateRecordsMetadataTest extends TestBase {
    private static boolean isCollaborationEnabled = false;
    private static final String APPLICATION_NAME = "storage service integration test for update records metadata";
    private static final String TENANT_NAME = TenantUtils.getTenantName();
    private static final long CURRENT_TIME_MILLIS = System.currentTimeMillis();
    private static String LEGAL_TAG_NAME;
    private static final String KIND = TENANT_NAME + ":ds:patchtest:1" + CURRENT_TIME_MILLIS;
    private static final String RECORD_PATCH_ID = TENANT_NAME + ":patchtest:1" + CURRENT_TIME_MILLIS;
    private static Long RECORD_PATCH_V1;
    private static Long RECORD_PATCH_V2;

    @Override
    public void setup() throws Exception {
        this.testUtils = new TokenTestUtils();
        this.configUtils = new ConfigUtils("test.properties");

        if (configUtils != null && !configUtils.getIsCollaborationEnabled()) {
            return;
        }
        isCollaborationEnabled = true;
        LEGAL_TAG_NAME = LegalTagUtils.createRandomName();
        LegalTagUtils.create(LEGAL_TAG_NAME, testUtils.getToken());

        //create records in different collaboration context
        RECORD_PATCH_V1 = createRecordInCollaborationContext_AndReturnVersion(RECORD_PATCH_ID, KIND, LEGAL_TAG_NAME, null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD_PATCH_V2 = createRecordInCollaborationContext_AndReturnVersion(RECORD_PATCH_ID, KIND, LEGAL_TAG_NAME, COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());

        //update record with tags in one collaboration context
        JsonObject updateBody = RecordUtil.buildUpdateTagBody(RECORD_PATCH_ID, "add", TAG_KEY + ":" + TAG_VALUE1);
        CloseableHttpResponse patchResponse = TestUtils.send("records", "PATCH", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TenantUtils.getTenantName(), testUtils.getToken()), updateBody.toString(), "");
        assertEquals(SC_OK, patchResponse.getCode());
    }

    @Test
    public void shouldMaintainAndUpdateRecordInRespectiveCollaborationContext() throws Exception {
        if (!isCollaborationEnabled) return;
        //assert record with no collaboration context
        CloseableHttpResponse getResponse = TestUtils.send("records/" + RECORD_PATCH_ID, "GET", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        String responseBody = assertRecordVersionAndReturnResponseBody(getResponse, RECORD_PATCH_V1);
        JsonObject resultObject = bodyToJsonObject(responseBody);
        assertNull(resultObject.get("tags"));
        //assert record with collaboration context
        getResponse = TestUtils.send("records/" + RECORD_PATCH_ID, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        responseBody = assertRecordVersionAndReturnResponseBody(getResponse, RECORD_PATCH_V2);
        resultObject = bodyToJsonObject(responseBody);
        assertTrue(resultObject.get("tags").getAsJsonObject().has(TAG_KEY));
        assertEquals(TAG_VALUE1, resultObject.get("tags").getAsJsonObject().get(TAG_KEY).getAsString());
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (!isCollaborationEnabled) return;
        TestUtils.send("records/" + RECORD_PATCH_ID, "DELETE", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_PATCH_ID, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        LegalTagUtils.delete(LEGAL_TAG_NAME, testUtils.getToken());

        this.testUtils = null;
        this.configUtils = null;
    }

    private static JsonObject bodyToJsonObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
