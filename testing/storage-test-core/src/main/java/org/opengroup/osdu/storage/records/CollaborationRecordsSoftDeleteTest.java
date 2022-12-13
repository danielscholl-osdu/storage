package org.opengroup.osdu.storage.records;

import com.google.gson.JsonArray;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Test;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.opengroup.osdu.storage.util.HeaderUtils.getHeadersWithxCollaboration;
import static org.opengroup.osdu.storage.util.TestUtils.assertRecordVersion;
import static org.opengroup.osdu.storage.util.TestUtils.createRecordInCollaborationContext_AndReturnVersion;

public abstract class CollaborationRecordsSoftDeleteTest extends TestBase {
    private static boolean isCollaborationEnabled = false;
    private static final String APPLICATION_NAME = "storage service integration test for soft delete";
    private static final String TENANT_NAME = TenantUtils.getTenantName();
    private static final long CURRENT_TIME_MILLIS = System.currentTimeMillis();
    private static final String COLLABORATION1_ID = UUID.randomUUID().toString();
    private static final String COLLABORATION2_ID = UUID.randomUUID().toString();
    private static final String RECORD_ID_1 = TENANT_NAME + ":inttest:1" + CURRENT_TIME_MILLIS;
    private static final String RECORD_ID_2 = TENANT_NAME + ":inttest:2" + CURRENT_TIME_MILLIS;
    private static final String RECORD_ID_3 = TENANT_NAME + ":inttest:3" + CURRENT_TIME_MILLIS;
    private static final String KIND = TENANT_NAME + ":ds:inttest:" + CURRENT_TIME_MILLIS;
    private static Long RECORD1_V1;
    private static Long RECORD1_V2;
    private static Long RECORD1_V3;
    private static Long RECORD1_V4;
    private static Long RECORD2_V1;
    private static Long RECORD2_V2;
    private static Long RECORD3_V1;
    private static Long RECORD3_V2;
    private static String LEGAL_TAG_NAME;

    @Override
    public void setup() throws Exception {
        if (configUtils != null && !configUtils.getIsCollaborationEnabled()) {
            return;
        }
        isCollaborationEnabled = true;
        LEGAL_TAG_NAME = LegalTagUtils.createRandomName();
        LegalTagUtils.create(LEGAL_TAG_NAME, testUtils.getToken());

        RECORD1_V1 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_1, KIND, LEGAL_TAG_NAME, COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD1_V2 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_1, KIND, LEGAL_TAG_NAME, COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD1_V3 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_1, KIND, LEGAL_TAG_NAME, null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD1_V4 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_1, KIND, LEGAL_TAG_NAME,COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());

        RECORD2_V1 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_2, KIND, LEGAL_TAG_NAME, COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD2_V2 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_2, KIND, LEGAL_TAG_NAME, COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());

        RECORD3_V1 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_3, KIND, LEGAL_TAG_NAME, COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
        RECORD3_V2 = createRecordInCollaborationContext_AndReturnVersion(RECORD_ID_3, KIND, LEGAL_TAG_NAME, COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken());
    }

    @After
    public void tearDown() throws Exception {
        if (!isCollaborationEnabled) return;
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_1, "DELETE", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_2, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_2, "DELETE", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_3, "DELETE", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID_3, "DELETE", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        LegalTagUtils.delete(LEGAL_TAG_NAME, testUtils.getToken());
    }

    @Test
    public void should_softDeleteSingleRecordWithinCollaborationContext_when_validRecordIdsAndCollaborationIdAreProvided() throws Exception {
        if (!isCollaborationEnabled) return;
        ClientResponse response = TestUtils.send("records/" + RECORD_ID_1 + ":delete", "POST", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());

        response = TestUtils.send("records/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());

        response = TestUtils.send("records/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(null, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD1_V3);

        response = TestUtils.send("records/" + RECORD_ID_1, "GET", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD1_V4);
    }

    @Test
    public void should_bulkSoftDeleteWithinCollaborationContext_when_validRecordIdsAndCollaborationIdAreProvided() throws Exception {
        if (!isCollaborationEnabled) return;
        JsonArray body = new JsonArray();
        body.add(RECORD_ID_2);
        body.add(RECORD_ID_3);
        ClientResponse response = TestUtils.send("records/delete", "POST", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), body.toString(), "");
        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());

        response = TestUtils.send("records/" + RECORD_ID_2, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());

        response = TestUtils.send("records/" + RECORD_ID_3, "GET", getHeadersWithxCollaboration(COLLABORATION1_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());

        response = TestUtils.send("records/" + RECORD_ID_2, "GET", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD2_V2);

        response = TestUtils.send("records/" + RECORD_ID_3, "GET", getHeadersWithxCollaboration(COLLABORATION2_ID, APPLICATION_NAME, TENANT_NAME, testUtils.getToken()), "", "");
        assertRecordVersion(response, RECORD3_V2);
    }
}
