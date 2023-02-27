package org.opengroup.osdu.storage.records;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public abstract class PatchRecordsTest extends TestBase {

    private static long NOW = System.currentTimeMillis();
    private static String LEGAL_TAG = LegalTagUtils.createRandomName();
    private static String LEGAL_TAG_TO_BE_PATCHED = LegalTagUtils.createRandomName();
    private static String KIND = TenantUtils.getFirstTenantName() + ":bulkupdate:test:1.1." + NOW;
    private static String RECORD_ID1 = TenantUtils.getFirstTenantName() + ":test:1.1." + NOW;
    private static String RECORD_ID2 = TenantUtils.getFirstTenantName() + ":test:1.2." + NOW;

    private static final DummyRecordsHelper RECORDS_HELPER = new DummyRecordsHelper();

    @Before
    public void setup() throws Exception {
        LegalTagUtils.create(LEGAL_TAG, testUtils.getToken());
        LegalTagUtils.create(LEGAL_TAG_TO_BE_PATCHED, testUtils.getToken());

        ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID1, KIND, LEGAL_TAG), "");
        assertEquals(HttpStatus.SC_CREATED, response.getStatus());

        response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID2, KIND, LEGAL_TAG), "");
        assertEquals(HttpStatus.SC_CREATED, response.getStatus());
    }

    @After
    public void tearDown() throws Exception {
        LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());
        LegalTagUtils.delete(LEGAL_TAG_TO_BE_PATCHED, testUtils.getToken());
        TestUtils.send("records/" + RECORD_ID1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID2, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    }

    @Test
    public void should_updateOnlyMetadata_whenOnlyMetadataIsPatched() throws Exception {
        List<String> records = new ArrayList<>();
        records.add(RECORD_ID1);
        records.add(RECORD_ID2);
        ClientResponse queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertQueryResponse(queryResponseObject, 2);
        String currentVersionRecord1 = queryResponseObject.records[0].version;
        String currentVersionRecord2 = queryResponseObject.records[1].version;

        ClientResponse patchResponse = TestUtils.sendWithCustomMediaType("records", "PATCH", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "application/json-patch+json", getPatchPayload(records, true, false), "");
        assertEquals(HttpStatus.SC_OK, patchResponse.getStatus());

        queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getStatus());

        queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertEquals(currentVersionRecord1, queryResponseObject.records[0].version);
        assertEquals(currentVersionRecord2, queryResponseObject.records[1].version);
        assertEquals(2, queryResponseObject.records.length);
        assertEquals(TestUtils.getAcl(), queryResponseObject.records[0].acl.viewers[0]);
        assertEquals(TestUtils.getAcl(), queryResponseObject.records[1].acl.viewers[0]);
        assertEquals(TestUtils.getIntegrationTesterAcl(), queryResponseObject.records[0].acl.owners[0]);
        assertEquals(TestUtils.getIntegrationTesterAcl(), queryResponseObject.records[1].acl.owners[0]);
        assertTrue(Arrays.stream(queryResponseObject.records[0].legal.legaltags).anyMatch(LEGAL_TAG::equals));
        assertTrue(Arrays.stream(queryResponseObject.records[1].legal.legaltags).anyMatch(LEGAL_TAG::equals));
        assertTrue(Arrays.stream(queryResponseObject.records[0].legal.legaltags).anyMatch(LEGAL_TAG_TO_BE_PATCHED::equals));
        assertTrue(Arrays.stream(queryResponseObject.records[1].legal.legaltags).anyMatch(LEGAL_TAG_TO_BE_PATCHED::equals));
        JsonObject tags = bodyToJsonObject(queryResponseObject.records[0].tags);
        assertTrue(tags.has("tag1"));
        assertTrue(tags.has("tag2"));
        assertEquals("value1", tags.get("tag1"));
        assertEquals("value2", tags.get("tag2"));
        tags = bodyToJsonObject(queryResponseObject.records[1].tags);
        assertTrue(tags.has("tag1"));
        assertTrue(tags.has("tag2"));
        assertEquals("value1", tags.get("tag1"));
        assertEquals("value2", tags.get("tag2"));
    }

    @Test
    public void should_updateDataAndMetadataVersion_whenOnlyDataIsPatched() throws Exception {
        List<String> records = new ArrayList<>();
        records.add(RECORD_ID1);
        ClientResponse queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertQueryResponse(queryResponseObject, 1);
        String currentVersionRecord1 = queryResponseObject.records[0].version;

        ClientResponse patchResponse = TestUtils.sendWithCustomMediaType("records", "PATCH", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "application/json-patch+json", getPatchPayload(records, false, true), "");
        assertEquals(HttpStatus.SC_OK, patchResponse.getStatus());

        queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getStatus());

        queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertNotEquals(currentVersionRecord1, queryResponseObject.records[0].version);
        assertTrue(queryResponseObject.records[0].data.containsKey("message"));
        assertEquals("test data", queryResponseObject.records[0].data.get("message"));
        assertQueryResponse(queryResponseObject, 1);
    }

    @Test
    public void should_updateBothMetadataAndData_whenDataAndMetadataArePatched() throws Exception {
        List<String> records = new ArrayList<>();
        records.add(RECORD_ID1);
        ClientResponse queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertQueryResponse(queryResponseObject, 1);
        String currentVersionRecord = queryResponseObject.records[0].version;

        ClientResponse patchResponse = TestUtils.sendWithCustomMediaType("records", "PATCH", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "application/json-patch+json", getPatchPayload(records, true, true), "");
        assertEquals(HttpStatus.SC_OK, patchResponse.getStatus());

        queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getStatus());

        queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertEquals(1, queryResponseObject.records.length);
        assertNotEquals(currentVersionRecord, queryResponseObject.records[0].version);
        assertEquals(TestUtils.getAcl(), queryResponseObject.records[0].acl.viewers[0]);
        assertEquals(TestUtils.getIntegrationTesterAcl(), queryResponseObject.records[0].acl.owners[0]);
        assertTrue(Arrays.stream(queryResponseObject.records[0].legal.legaltags).anyMatch(LEGAL_TAG::equals));
        assertTrue(Arrays.stream(queryResponseObject.records[0].legal.legaltags).anyMatch(LEGAL_TAG_TO_BE_PATCHED::equals));
        JsonObject tags = bodyToJsonObject(queryResponseObject.records[0].tags);
        assertTrue(tags.has("tag1"));
        assertTrue(tags.has("tag2"));
        assertEquals("value1", tags.get("tag1"));
        assertEquals("value2", tags.get("tag2"));
        assertTrue(queryResponseObject.records[0].data.containsKey("message"));
        assertEquals("test data", queryResponseObject.records[0].data.get("message"));

    }

    private ClientResponse queryRecordsResponse(List<String> recordIds) throws Exception {
        JsonArray records = new JsonArray();
        for(String recordId : recordIds) {
            records.add(recordId);
        }
        JsonObject queryBody = new JsonObject();
        queryBody.add("records", records);

        Map<String, String> queryHeader = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        queryHeader.put("frame-of-reference", "none");
        return TestUtils.send("query/records:batch", "POST", queryHeader, queryBody.toString(), "");
    }

    private void assertQueryResponse(DummyRecordsHelper.ConvertedRecordsMock queryResponse, int expectedRecordCount) {
        assertEquals(expectedRecordCount, queryResponse.records.length);
        assertEquals(TestUtils.getAcl(), queryResponse.records[0].acl.viewers[0]);
        assertEquals(TestUtils.getAcl(), queryResponse.records[0].acl.owners[0]);
    }

    private String getPatchPayload(List<String> records, boolean isMetaUpdate, boolean isDataUpdate) {
        JsonArray recordsJson = new JsonArray();
        for(String record : records) {
            recordsJson.add(record);
        }

        JsonArray ops = new JsonArray();
        if(isMetaUpdate) {
            ops.add(getAddTagsPatchOp());
            ops.add(getReplaceAclOwnersPatchOp());
            ops.add(getAddLegaltagsPatchOp());
        }
        if(isDataUpdate) {
            ops.add(getReplaceDataPatchOp());
        }

        return getPatchrequestBody(recordsJson, ops);
    }

    private JsonObject getAddTagsPatchOp() {
        JsonObject tagsValue = new JsonObject();
        tagsValue.addProperty("tag1", "value1");
        tagsValue.addProperty("tag2", "value2");
        JsonObject addTagsPatch = new JsonObject();
        addTagsPatch.addProperty("op", "add");
        addTagsPatch.addProperty("path", "/tags");
        addTagsPatch.add("value", tagsValue);
        return addTagsPatch;
    }

    private JsonObject getReplaceAclOwnersPatchOp() {
        JsonArray newAclValue = new JsonArray();
        newAclValue.add(TestUtils.getIntegrationTesterAcl());
        JsonObject replaceAclPatch = new JsonObject();
        replaceAclPatch.addProperty("op", "replace");
        replaceAclPatch.addProperty("path", "/acl/owners");
        replaceAclPatch.add("value", newAclValue);
        return replaceAclPatch;
    }

    private JsonObject getAddLegaltagsPatchOp() {
        JsonObject replaceAclPatch = new JsonObject();
        replaceAclPatch.addProperty("op", "add");
        replaceAclPatch.addProperty("path", "/legal/legaltags/-");
        replaceAclPatch.addProperty("value", LEGAL_TAG_TO_BE_PATCHED);
        return replaceAclPatch;
    }

    private JsonObject getReplaceDataPatchOp() {
        JsonObject newDataValue = new JsonObject();
        JsonObject innerDataValue = new JsonObject();
        innerDataValue.addProperty("message", "test data");
        newDataValue.add("data", innerDataValue);
        JsonObject replaceDataPatch = new JsonObject();
        replaceDataPatch.addProperty("op", "replace");
        replaceDataPatch.addProperty("path", "/data");
        replaceDataPatch.add("value", newDataValue);
        return replaceDataPatch;
    }

    private String getPatchrequestBody(JsonArray recordsJson, JsonArray ops) {
        JsonObject query = new JsonObject();
        query.add("ids", recordsJson);

        JsonObject updateBody = new JsonObject();
        updateBody.add("query", query);
        updateBody.add("ops", ops);

        return updateBody.toString();
    }

    private JsonObject bodyToJsonObject(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }
}
