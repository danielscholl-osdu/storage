// Copyright 2017-2023, Schlumberger
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

public final class PatchRecordsTest extends TestBase {

    private static long NOW = System.currentTimeMillis();
    private static String LEGAL_TAG = LegalTagUtils.createRandomName();
    private static String LEGAL_TAG_TO_BE_PATCHED = LegalTagUtils.createRandomName() + "1";
    private static String KIND = TenantUtils.getFirstTenantName() + ":bulkupdate:test:1.1." + NOW;
    private static String KIND_TO_BE_PATCHED = TenantUtils.getFirstTenantName() + ":bulkupdate:test:1.2." + NOW;
    private static String RECORD_ID1 = TenantUtils.getFirstTenantName() + ":test:1.1." + NOW;
    private static String RECORD_ID2 = TenantUtils.getFirstTenantName() + ":test:1.2." + NOW;
    private static final int MAX_OP_NUMBER = 100;

    private static final DummyRecordsHelper RECORDS_HELPER = new DummyRecordsHelper();

    @BeforeEach
    public void setup() throws Exception {
        this.testUtils = new TokenTestUtils();
        LegalTagUtils.create(LEGAL_TAG, testUtils.getToken());
        LegalTagUtils.create(LEGAL_TAG_TO_BE_PATCHED, testUtils.getToken());

        CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID1, KIND, LEGAL_TAG), "");
        assertEquals(HttpStatus.SC_CREATED, response.getCode());

        response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createDefaultJsonRecord(RECORD_ID2, KIND, LEGAL_TAG), "");
        assertEquals(HttpStatus.SC_CREATED, response.getCode());
    }

    @AfterEach
    public void tearDown() throws Exception {
        LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());
        LegalTagUtils.delete(LEGAL_TAG_TO_BE_PATCHED, testUtils.getToken());
        TestUtils.send("records/" + RECORD_ID1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        TestUtils.send("records/" + RECORD_ID2, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        this.testUtils = null;
    }

    @Test
    public void should_updateOnlyMetadata_whenOnlyMetadataIsPatched() throws Exception {
        List<String> records = new ArrayList<>();
        records.add(RECORD_ID1);
        records.add(RECORD_ID2);
        CloseableHttpResponse queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getCode());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertQueryResponse(queryResponseObject, 2);
        String currentVersionRecord1 = queryResponseObject.records[0].version;
        String currentVersionRecord2 = queryResponseObject.records[1].version;
        assertEquals(null, queryResponseObject.records[0].modifyTime);
        assertEquals(null, queryResponseObject.records[0].modifyUser);

        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records", "PATCH", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "application/json-patch+json", getPatchPayload(records, true, false), "");
        assertEquals(HttpStatus.SC_OK, patchResponse.getCode());

        queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getCode());

        queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        //modifyUser and modifyTime are not reflected appropriately, please refer to this issue https://community.opengroup.org/osdu/platform/system/storage/-/issues/171
        assertEquals(currentVersionRecord1, queryResponseObject.records[0].version);
        assertEquals(currentVersionRecord2, queryResponseObject.records[1].version);
        assertEquals(2, queryResponseObject.records.length);
        assertEquals(KIND_TO_BE_PATCHED, queryResponseObject.records[0].kind);
        assertEquals(KIND_TO_BE_PATCHED, queryResponseObject.records[1].kind);
        assertEquals(TestUtils.getAcl(), queryResponseObject.records[0].acl.viewers[0]);
        assertEquals(TestUtils.getAcl(), queryResponseObject.records[1].acl.viewers[0]);
        assertEquals(TestUtils.getIntegrationTesterAcl(), queryResponseObject.records[0].acl.owners[0]);
        assertEquals(TestUtils.getIntegrationTesterAcl(), queryResponseObject.records[1].acl.owners[0]);
        assertTrue(Arrays.stream(queryResponseObject.records[0].legal.legaltags).anyMatch(LEGAL_TAG::equals));
        assertTrue(Arrays.stream(queryResponseObject.records[1].legal.legaltags).anyMatch(LEGAL_TAG::equals));
        assertTrue(Arrays.stream(queryResponseObject.records[0].legal.legaltags).anyMatch(LEGAL_TAG_TO_BE_PATCHED::equals));
        assertTrue(Arrays.stream(queryResponseObject.records[1].legal.legaltags).anyMatch(LEGAL_TAG_TO_BE_PATCHED::equals));
        Map<String, String> tags = queryResponseObject.records[0].tags;
        assertTrue(tags.containsKey("tag1"));
        assertTrue(tags.containsKey("tag2"));
        assertEquals("value1", tags.get("tag1"));
        assertEquals("value2", tags.get("tag2"));
        tags = queryResponseObject.records[1].tags;
        assertTrue(tags.containsKey("tag1"));
        assertTrue(tags.containsKey("tag2"));
        assertEquals("value1", tags.get("tag1"));
        assertEquals("value2", tags.get("tag2"));
    }

    @Test
    public void should_updateDataAndMetadataVersion_whenOnlyDataIsPatched() throws Exception {
        List<String> records = new ArrayList<>();
        records.add(RECORD_ID1);
        CloseableHttpResponse queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getCode());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertQueryResponse(queryResponseObject, 1);
        String currentVersionRecord1 = queryResponseObject.records[0].version;

        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records", "PATCH", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "application/json-patch+json", getPatchPayload(records, false, true), "");
        assertEquals(HttpStatus.SC_OK, patchResponse.getCode());

        queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getCode());

        queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertNotEquals(currentVersionRecord1, queryResponseObject.records[0].version);
        assertEquals(KIND, queryResponseObject.records[0].kind);
        assertTrue(queryResponseObject.records[0].data.containsKey("data"));
        assertTrue(queryResponseObject.records[0].data.get("data").toString().equals("{message=test data}"));
        assertQueryResponse(queryResponseObject, 1);
    }

    @Test
    public void should_updateBothMetadataAndData_whenDataAndMetadataArePatched() throws Exception {
        List<String> records = new ArrayList<>();
        records.add(RECORD_ID1);
        CloseableHttpResponse queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getCode());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertQueryResponse(queryResponseObject, 1);
        String currentVersionRecord = queryResponseObject.records[0].version;

        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records", "PATCH", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "application/json-patch+json", getPatchPayload(records, true, true), "");
        assertEquals(HttpStatus.SC_OK, patchResponse.getCode());

        queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getCode());

        queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertEquals(1, queryResponseObject.records.length);
        assertNotEquals(currentVersionRecord, queryResponseObject.records[0].version);
        assertEquals(KIND_TO_BE_PATCHED, queryResponseObject.records[0].kind);
        assertEquals(TestUtils.getAcl(), queryResponseObject.records[0].acl.viewers[0]);
        assertEquals(TestUtils.getIntegrationTesterAcl(), queryResponseObject.records[0].acl.owners[0]);
        assertTrue(Arrays.stream(queryResponseObject.records[0].legal.legaltags).anyMatch(LEGAL_TAG::equals));
        assertTrue(Arrays.stream(queryResponseObject.records[0].legal.legaltags).anyMatch(LEGAL_TAG_TO_BE_PATCHED::equals));
        Map<String, String> tags = queryResponseObject.records[0].tags;
        assertTrue(tags.containsKey("tag1"));
        assertTrue(tags.containsKey("tag2"));
        assertEquals("value1", tags.get("tag1"));
        assertEquals("value2", tags.get("tag2"));
        assertTrue(queryResponseObject.records[0].data.containsKey("data"));
        assertTrue(queryResponseObject.records[0].data.get("data").toString().equals("{message=test data}"));

    }

    @Test
    public void should_update_whenNumberOfPatchOperationsIsMaximum() throws Exception {
        List<String> records = new ArrayList<>();
        records.add(RECORD_ID1);
        CloseableHttpResponse queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getCode());

        DummyRecordsHelper.ConvertedRecordsMock queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertQueryResponse(queryResponseObject, 1);
        String currentVersionRecord = queryResponseObject.records[0].version;

        CloseableHttpResponse patchResponse = TestUtils.sendWithCustomMediaType("records", "PATCH", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "application/json-patch+json", getMaximumPatchOperationsPayload(records), "");
        assertEquals(HttpStatus.SC_OK, patchResponse.getCode());

        queryResponse = queryRecordsResponse(records);
        assertEquals(HttpStatus.SC_OK, queryResponse.getCode());

        queryResponseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(queryResponse);
        assertEquals(1, queryResponseObject.records.length);
        assertEquals(currentVersionRecord, queryResponseObject.records[0].version);
        Map<String, String> tags = queryResponseObject.records[0].tags;
        assertTrue(tags.containsKey("testTag0"));
        assertTrue(tags.containsKey("testTag99"));
        assertEquals("value0", tags.get("testTag0"));
        assertEquals("value99", tags.get("testTag99"));
    }

    //TODO: add a test to validate same 'op' and 'path' and assert expected behavior

    private CloseableHttpResponse queryRecordsResponse(List<String> recordIds) throws Exception {
        JsonArray records = new JsonArray();
        for (String recordId : recordIds) {
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
        for (String record : records) {
            recordsJson.add(record);
        }

        JsonArray ops = new JsonArray();
        if (isMetaUpdate) {
            ops.add(getAddTagsPatchOp());
            ops.add(getReplaceAclOwnersPatchOp());
            ops.add(getAddLegaltagsPatchOp());
            ops.add(getReplaceKindPatchOp());
        }
        if (isDataUpdate) {
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

    private JsonObject getReplaceKindPatchOp() {
        JsonObject replaeKindPatch = new JsonObject();
        replaeKindPatch.addProperty("op", "replace");
        replaeKindPatch.addProperty("path", "/kind");
        replaeKindPatch.addProperty("value", KIND_TO_BE_PATCHED);
        return replaeKindPatch;
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

    private String getMaximumPatchOperationsPayload(List<String> records) {
        JsonArray recordsJson = new JsonArray();
        for (String record : records) {
            recordsJson.add(record);
        }
        JsonArray ops = new JsonArray();
        for (int i = 0; i < MAX_OP_NUMBER; i++) {
            JsonObject addTagOperation = new JsonObject();
            addTagOperation.addProperty("op", "add");
            addTagOperation.addProperty("path", "/tags/testTag" + i);
            addTagOperation.addProperty("value", "value" + i);
            ops.add(addTagOperation);
        }
        return getPatchrequestBody(recordsJson, ops);
    }

    private String getPatchrequestBody(JsonArray recordsJson, JsonArray ops) {
        JsonObject query = new JsonObject();
        query.add("ids", recordsJson);

        JsonObject updateBody = new JsonObject();
        updateBody.add("query", query);
        updateBody.add("ops", ops);

        return updateBody.toString();
    }

}
