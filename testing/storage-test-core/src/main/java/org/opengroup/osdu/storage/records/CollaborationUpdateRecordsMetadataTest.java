package org.opengroup.osdu.storage.records;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;
import org.junit.After;
import org.junit.Test;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opengroup.osdu.storage.records.CollaborationRecordsPurgeTest.COLLABORATION1_ID;
import static org.opengroup.osdu.storage.records.UpdateRecordsMetadataTest.TAG_KEY;
import static org.opengroup.osdu.storage.records.UpdateRecordsMetadataTest.TAG_VALUE1;
import static org.opengroup.osdu.storage.util.HeaderUtils.getHeadersWithxCollaboration;
import static org.opengroup.osdu.storage.util.TestUtils.assertRecordVersionAndReturnResponseBody;
import static org.opengroup.osdu.storage.util.TestUtils.createRecordInCollaborationContext_AndReturnVersion;

public abstract class CollaborationUpdateRecordsMetadataTest extends TestBase {
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
        LEGAL_TAG_NAME = LegalTagUtils.createRandomName();
        LegalTagUtils.create(LEGAL_TAG_NAME, testUtils.getToken());

        //create records in different collaboration context
        RECORD_PATCH_V1 = createRecordInCollaborationContext_AndReturnVersion(RECORD_PATCH_ID, KIND, LEGAL_TAG_NAME, null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD_PATCH_V2 = createRecordInCollaborationContext_AndReturnVersion(RECORD_PATCH_ID, KIND, LEGAL_TAG_NAME, COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());

        //update record with tags in one collaboration context
        JsonObject updateBody = RecordUtil.buildUpdateTagBody(RECORD_PATCH_ID, "add", TAG_KEY + ":" + TAG_VALUE1);
        ClientResponse patchResponse = TestUtils.send("records", "PATCH", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TenantUtils.getTenantName(), testUtils.getToken()), updateBody.toString(), "");
        assertEquals(SC_OK, patchResponse.getStatus());
    }

    @Test
    public void shouldMaintainAndUpdateRecordInRespctiveCollaborationContext() throws Exception {
        //assert record with no collaboration context
        ClientResponse getResponse = TestUtils.send("records/" + RECORD_PATCH_ID, "GET", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
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

    @After
    public void tearDown() throws Exception {
        TestUtils.send("records/" + RECORD_PATCH_ID, "DELETE", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_PATCH_ID, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        LegalTagUtils.delete(LEGAL_TAG_NAME, testUtils.getToken());
    }

    private static JsonObject bodyToJsonObject(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }
}
