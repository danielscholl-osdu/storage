package org.opengroup.osdu.storage.query;

import static org.junit.Assert.assertEquals;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengroup.osdu.storage.util.AzureTestUtils;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.DummyRecordsHelper.RecordResultMock;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class TestEncodedRecordIdQuery extends TestBase {

    private static final AzureTestUtils azureTestUtils = new AzureTestUtils();

    protected static final String RECORD = "records";
    protected static final Long NOW = System.currentTimeMillis();
    protected static final String LEGAL_TAG = LegalTagUtils.createRandomName();
    protected static final String KIND = TenantUtils.getTenantName() + ":test:endtoend:1.1." + NOW;

    // Record IDs with URL-encoded characters
    protected static final String RECORD_ID_WITH_SPECIAL_CHARS = TenantUtils.getTenantName() + ":endtoend:specialchars%20:1.1." + NOW;
    protected static final String RECORD_ID_WITH_SPECIAL_CHARS_2 = TenantUtils.getTenantName() + ":endtoend:specialcharsagain%20:1.1." + NOW;

    @BeforeClass
    public static void classSetup() throws Exception {
        LegalTagUtils.create(LEGAL_TAG, azureTestUtils.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        var token = azureTestUtils.getToken();
        LegalTagUtils.delete(LEGAL_TAG, token);
        // Clean up all test records
        TestUtils.send("records/" + URLEncoder.encode(RECORD_ID_WITH_SPECIAL_CHARS, StandardCharsets.UTF_8), 
                        "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
        TestUtils.send("records/" + URLEncoder.encode(RECORD_ID_WITH_SPECIAL_CHARS_2, StandardCharsets.UTF_8),
                    "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AzureTestUtils();
    }

    @After
    @Override
    public void tearDown() throws Exception {
    this.testUtils = null;
    }

    @Test
    public void should_preserveEncodedIdWithSpecialChars_when_recordIsQueriedViaGetAPI() throws Exception {
        String recordId = RECORD_ID_WITH_SPECIAL_CHARS;
        
        // Create record with encoded ID
        CloseableHttpResponse response = TestUtils.send("records", "PUT", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createJsonRecordWithData(recordId, KIND, LEGAL_TAG, "v1"), "");
        assertEquals(HttpStatus.SC_CREATED, response.getCode());

        // Verify the response contains the original (unencoded) ID
        JsonObject createResponse = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
        JsonArray recordIds = createResponse.getAsJsonArray("recordIds");
        assertEquals(1, recordIds.size());
        assertEquals(recordId, recordIds.get(0).getAsString());

        // Get record using URL-encoded ID in the path
        String encodedRecordId = URLEncoder.encode(recordId, StandardCharsets.UTF_8);
        response = TestUtils.send("records/" + encodedRecordId, "GET", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        // Verify the response contains the original (unencoded) ID
        JsonObject getResponse = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
        assertEquals(recordId, getResponse.get("id").getAsString());
    }

    @Test
    public void should_preserveEncodedIdWithSpecialChars_when_recordIsQueriedViaPostAPI() throws Exception {
        String recordId = RECORD_ID_WITH_SPECIAL_CHARS_2;
        
        // Create record with encoded ID
        CloseableHttpResponse response = TestUtils.send("records", "PUT", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createJsonRecordWithData(recordId, KIND, LEGAL_TAG, "v1"), "");
        assertEquals(HttpStatus.SC_CREATED, response.getCode());

        // Verify the response contains the original (unencoded) ID
        JsonObject createResponse = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
        JsonArray recordIds = createResponse.getAsJsonArray("recordIds");
        assertEquals(1, recordIds.size());
        assertEquals(recordId, recordIds.get(0).getAsString());

        // Test query records with encoded ID
        JsonArray records = new JsonArray();
        records.add(recordId); // Use original ID in query
        JsonArray attributes = new JsonArray();

        JsonObject queryBody = new JsonObject();
        queryBody.add("records", records);
        queryBody.add("attributes", attributes);

        response = TestUtils.send("query/records", "POST", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                queryBody.toString(), "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        DummyRecordsHelper.RecordsMock responseObject = new DummyRecordsHelper().getRecordsMockFromResponse(response);
        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.invalidRecords.length);
        assertEquals(0, responseObject.retryRecords.length);

        RecordResultMock result = responseObject.records[0];
        assertEquals(recordId, result.id); // Verify original ID is preserved
    }
}
