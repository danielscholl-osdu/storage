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

package org.opengroup.osdu.storage.query;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.*;
import org.opengroup.osdu.storage.util.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestPostFetchRecordsIntegration extends PostFetchRecordsIntegrationTests {

    private static final AzureTestUtils azureTestUtils = new AzureTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        PostFetchRecordsIntegrationTests.classSetup(azureTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        PostFetchRecordsIntegrationTests.classTearDown(azureTestUtils.getToken());
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

    //TODO: remove all the overridden tests when Conversion service is deployed to Azure
    @Override
    @Test
    public void should_returnConvertedRecords_whenConversionRequiredAndNoError() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithReference(2, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        CloseableHttpResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getCode());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);
        records.add(recordId + 1);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        CloseableHttpResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(2, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);

        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(3, responseObject.records[0].data.size());

        CloseableHttpResponse deleteResponse1 = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse1.getCode());
        CloseableHttpResponse deleteResponse2 = TestUtils.send("records/" + recordId + 1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse2.getCode());
    }

    @Override
    @Test
    public void should_returnOriginalRecordsAndConversionStatusAsNoMeta_whenConversionRequiredAndNoMetaBlockInRecord() throws Exception{
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordNoMetaBlock(2, recordId, KIND, LEGAL_TAG);
        CloseableHttpResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getCode());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);
        records.add(recordId + 1);
        records.add("nonexisting:id");

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        CloseableHttpResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(2, responseObject.records.length);
        assertEquals(1, responseObject.notFound.length);
        assertEquals("nonexisting:id", responseObject.notFound[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(3, responseObject.records[0].data.size());
        List<DummyRecordsHelper.RecordStatusMock> conversionStatuses = responseObject.conversionStatuses;
        CloseableHttpResponse deleteResponse1 = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse1.getCode());
        CloseableHttpResponse deleteResponse2 = TestUtils.send("records/" + recordId + 1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse2.getCode());
    }

    @Override
    @Test
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndConversionErrorExists() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordMissingValue(2, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        CloseableHttpResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getCode());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);
        records.add(recordId + 1);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        CloseableHttpResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(2, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(2, responseObject.records[0].data.size());
        CloseableHttpResponse deleteResponse1 = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse1.getCode());
        CloseableHttpResponse deleteResponse2 = TestUtils.send("records/" + recordId + 1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse2.getCode());
    }

    @Override
    @Test
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndNestedPropertyProvidedInMetaBlock() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithNestedProperty(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        CloseableHttpResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getCode());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        CloseableHttpResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getCode());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
//        List<String> conversionStatus = (List<String>)responseObject.conversionStatuses.get(RECORD_ID + 8);
//        assertEquals("nested property projectOutlineLocalGeographic converted successfully", conversionStatus.get(0));

        TestUtils.send("records/" + recordId + 8, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    }
}
