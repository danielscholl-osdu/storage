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
import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class PostFetchRecordsIntegrationTests extends TestBase {
    protected static final long NOW = System.currentTimeMillis();

    protected static final String RECORD_ID_PREFIX = TenantUtils.getFirstTenantName() + ":query:";
    protected static final String KIND = TenantUtils.getTenantName() + ":ds:query:1.0." + NOW;
    protected static final String LEGAL_TAG = LegalTagUtils.createRandomName();
    protected static final DummyRecordsHelper RECORDS_HELPER = new DummyRecordsHelper();

    protected static final String PERSISTABLE_REFERENCE = "%7B%22LB_CRS%22%3A%22%257B%2522WKT%2522%253A%2522PROJCS%255B%255C%2522British_National_Grid%255C%2522%252CGEOGCS%255B%255C%2522GCS_OSGB_1936%255C%2522%252CDATUM%255B%255C%2522D_OSGB_1936%255C%2522%252CSPHEROID%255B%255C%2522Airy_1830%255C%2522%252C6377563.396%252C299.3249646%255D%255D%252CPRIMEM%255B%255C%2522Greenwich%255C%2522%252C0.0%255D%252CUNIT%255B%255C%2522Degree%255C%2522%252C0.0174532925199433%255D%255D%252CPROJECTION%255B%255C%2522Transverse_Mercator%255C%2522%255D%252CPARAMETER%255B%255C%2522False_Easting%255C%2522%252C400000.0%255D%252CPARAMETER%255B%255C%2522False_Northing%255C%2522%252C-100000.0%255D%252CPARAMETER%255B%255C%2522Central_Meridian%255C%2522%252C-2.0%255D%252CPARAMETER%255B%255C%2522Scale_Factor%255C%2522%252C0.9996012717%255D%252CPARAMETER%255B%255C%2522Latitude_Of_Origin%255C%2522%252C49.0%255D%252CUNIT%255B%255C%2522Meter%255C%2522%252C1.0%255D%252CAUTHORITY%255B%255C%2522EPSG%255C%2522%252C27700%255D%255D%2522%252C%2522Type%2522%253A%2522LBCRS%2522%252C%2522EngineVersion%2522%253A%2522PE_10_3_1%2522%252C%2522AuthorityCode%2522%253A%257B%2522Authority%2522%253A%2522EPSG%2522%252C%2522Code%2522%253A%252227700%2522%257D%252C%2522Name%2522%253A%2522British_National_Grid%2522%257D%22%2C%22TRF%22%3A%22%257B%2522WKT%2522%253A%2522GEOGTRAN%255B%255C%2522OSGB_1936_To_WGS_1984_Petroleum%255C%2522%252CGEOGCS%255B%255C%2522GCS_OSGB_1936%255C%2522%252CDATUM%255B%255C%2522D_OSGB_1936%255C%2522%252CSPHEROID%255B%255C%2522Airy_1830%255C%2522%252C6377563.396%252C299.3249646%255D%255D%252CPRIMEM%255B%255C%2522Greenwich%255C%2522%252C0.0%255D%252CUNIT%255B%255C%2522Degree%255C%2522%252C0.0174532925199433%255D%255D%252CGEOGCS%255B%255C%2522GCS_WGS_1984%255C%2522%252CDATUM%255B%255C%2522D_WGS_1984%255C%2522%252CSPHEROID%255B%255C%2522WGS_1984%255C%2522%252C6378137.0%252C298.257223563%255D%255D%252CPRIMEM%255B%255C%2522Greenwich%255C%2522%252C0.0%255D%252CUNIT%255B%255C%2522Degree%255C%2522%252C0.0174532925199433%255D%255D%252CMETHOD%255B%255C%2522Position_Vector%255C%2522%255D%252CPARAMETER%255B%255C%2522X_Axis_Translation%255C%2522%252C446.448%255D%252CPARAMETER%255B%255C%2522Y_Axis_Translation%255C%2522%252C-125.157%255D%252CPARAMETER%255B%255C%2522Z_Axis_Translation%255C%2522%252C542.06%255D%252CPARAMETER%255B%255C%2522X_Axis_Rotation%255C%2522%252C0.15%255D%252CPARAMETER%255B%255C%2522Y_Axis_Rotation%255C%2522%252C0.247%255D%252CPARAMETER%255B%255C%2522Z_Axis_Rotation%255C%2522%252C0.842%255D%252CPARAMETER%255B%255C%2522Scale_Difference%255C%2522%252C-20.489%255D%252CAUTHORITY%255B%255C%2522EPSG%255C%2522%252C1314%255D%255D%2522%252C%2522Type%2522%253A%2522STRF%2522%252C%2522EngineVersion%2522%253A%2522PE_10_3_1%2522%252C%2522AuthorityCode%2522%253A%257B%2522Authority%2522%253A%2522EPSG%2522%252C%2522Code%2522%253A%25221314%2522%257D%252C%2522Name%2522%253A%2522OSGB_1936_To_WGS_1984_Petroleum%2522%257D%22%2C%22Type%22%3A%22EBCRS%22%2C%22EngineVersion%22%3A%22PE_10_3_1%22%2C%22Name%22%3A%22OSGB+1936+*+UKOOA-Pet+%2F+British+National+Grid+%5B27700%2C1314%5D%22%2C%22AuthorityCode%22%3A%7B%22Authority%22%3A%22MyCompany%22%2C%22Code%22%3A%2227700006%22%7D%7D";
    private static final String DATETIME_PERSISTABLE_REFERENCE = "{\"type\":\"DAT\",\"format\":\"YYYY-MM-DD\"}";
    private static final String UNIT_PERSISTABLE_REFERENCE = "{\"abcd\":{\"a\":0.0,\"b\":0.3048,\"c\":1.0,\"d\":0.0},\"symbol\":\"ft\",\"baseMeasurement\":{\"ancestry\":\"L\",\"type\":\"UM\"},\"type\":\"UAD\"}";

    public static void classSetup(String token) throws Exception {
        LegalTagUtils.create(LEGAL_TAG, token);
    }

    public static void classTearDown(String token) throws Exception {
        LegalTagUtils.delete(LEGAL_TAG, token);
    }
    
    @Test
    public void should_returnSingleRecordMatching_when_noConversionRequired() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithReference(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "none");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);

        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(0, responseObject.conversionStatuses.size());

        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(recordId + 0, responseObject.records[0].id);
        assertEquals(3, responseObject.records[0].data.size());
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());

        ClientResponse deleteResponse = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse.getStatus());
    }

    @Test
    public void should_returnRecordMatchingAndRecordNotFound_when_noConversionRequired() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithReference(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);
        records.add("nonexisting:id");

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "none");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);

        assertEquals(1, responseObject.records.length);
        assertEquals(1, responseObject.notFound.length);
        assertEquals(0, responseObject.conversionStatuses.size());

        assertEquals("nonexisting:id", responseObject.notFound[0]);
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(recordId + 0, responseObject.records[0].id);
        assertEquals(3, responseObject.records[0].data.size());
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());

        ClientResponse deleteResponse = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse.getStatus());
    }

    @Test
    public void should_return400BadRequest_when_moreThan20RecordsRequiredAndNoConversionRequired() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();

        JsonArray records = new JsonArray();
        for (int i = 0; i < 21; i++) {
            records.add(recordId + String.valueOf(i));
        }

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "none");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    }

    @Test
    @Ignore // Ignoring the test for now, once we have CRS converter we should enable this test
    public void should_returnConvertedRecords_whenConversionRequiredAndNoError() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithReference(2, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);
        records.add(recordId + 1);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(2, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(2, responseObject.conversionStatuses.size());

        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(3, responseObject.records[0].data.size());

        ClientResponse deleteResponse1 = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse1.getStatus());
        ClientResponse deleteResponse2 = TestUtils.send("records/" + recordId + 1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse2.getStatus());

    }

    @Ignore // Ignoring the test for now, once we have CRS converter we should enable this test
    @Test
    public void should_returnConvertedRecords_whenConversionRequiredAndNoErrorWithMultiplePairOfCoordinates() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithMultiplePairOfCoordinates(2, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);
        records.add(recordId + 1);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(2, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);

        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(5, responseObject.records[0].data.size());

        ClientResponse deleteResponse1 = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse1.getStatus());
        ClientResponse deleteResponse2 = TestUtils.send("records/" + recordId + 1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse2.getStatus());

    }

    @Ignore // Ignoring the test for now, once we have CRS converter we should enable this test
    @Test
    public void should_returnOriginalRecordsAndConversionStatusAsNoMeta_whenConversionRequiredAndNoMetaBlockInRecord() throws Exception{
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordNoMetaBlock(2, recordId, KIND, LEGAL_TAG);
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);
        records.add(recordId + 1);
        records.add("nonexisting:id");

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(2, responseObject.records.length);
        assertEquals(1, responseObject.notFound.length);
        assertEquals(2, responseObject.conversionStatuses.size());
        assertEquals("nonexisting:id", responseObject.notFound[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(3, responseObject.records[0].data.size());
        List<DummyRecordsHelper.RecordStatusMock> conversionStatuses = responseObject.conversionStatuses;
        assertEquals("No Meta Block in This Record.", conversionStatuses.get(0).errors.get(0));
        ClientResponse deleteResponse1 = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse1.getStatus());
        ClientResponse deleteResponse2 = TestUtils.send("records/" + recordId + 1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse2.getStatus());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndConversionErrorExists() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordMissingValue(2, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);
        records.add(recordId + 1);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(2, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(2, responseObject.conversionStatuses.size());
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(2, responseObject.records[0].data.size());
        List<DummyRecordsHelper.RecordStatusMock> conversionStatuses = responseObject.conversionStatuses;
        assertEquals("CRS conversion: Unknown coordinate pair 'z'.", conversionStatuses.get(0).errors.get(1));
        assertEquals("CRS conversion: property 'Y' is missing in datablock, no conversion applied to this property and its corresponding pairing property.", conversionStatuses.get(0).errors.get(0));
        ClientResponse deleteResponse1 = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse1.getStatus());
        ClientResponse deleteResponse2 = TestUtils.send("records/" + recordId + 1, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse2.getStatus());
    }

    @Test
    @Ignore // Ignoring the test for now, once we have CRS converter we should enable this test
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndNestedPropertyProvidedInMetaBlock() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithNestedProperty(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(1, responseObject.conversionStatuses.size());
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
//        List<String> conversionStatus = (List<String>)responseObject.conversionStatuses.get(RECORD_ID + 8);
//        assertEquals("nested property projectOutlineLocalGeographic converted successfully", conversionStatus.get(0));

        ClientResponse deleteResponse = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse.getStatus());
    }
    @Ignore // Ignoring the test for now, once we have CRS converter we should enable this test
    @Test
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndNestedPropertyProvidedInMetaBlock1() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithNestedProperty(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),
                "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
//        List<String> conversionStatus = (List<String>)responseObject.conversionStatuses.get(RECORD_ID + 8);
//        assertEquals("nested property projectOutlineLocalGeographic converted successfully", conversionStatus.get(0));

        ClientResponse deleteResponse = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse.getStatus());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenDateAndFormatProvidedInMetaBlock() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordsWithDateFormat(1, recordId, KIND, LEGAL_TAG, "yyyy-MM-dd", "creationDate", "2019-08-03", DATETIME_PERSISTABLE_REFERENCE);
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 0);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),"");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);

        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());

        ClientResponse deleteResponse = TestUtils.send("records/" + recordId + 0, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse.getStatus());
    }

    @Ignore // Ignoring the test for now, once we have CRS converter we should enable this test
    @Test
    public void should_returnRecordsAndConversionStatus_whenNestedArrayOfPropertiesProvidedWithoutError() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithNestedArrayOfProperties(1, recordId, KIND, LEGAL_TAG, UNIT_PERSISTABLE_REFERENCE, "Unit");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 12);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),"");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(1, responseObject.conversionStatuses.size());
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(2, responseObject.records[0].data.size());
        List<DummyRecordsHelper.RecordStatusMock> conversionStatuses = responseObject.conversionStatuses;
        assertEquals("SUCCESS", conversionStatuses.get(0).status);

        ClientResponse deleteResponse = TestUtils.send("records/" + recordId + 12, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse.getStatus());
    }

    @Ignore // Ignoring the test for now, once we have CRS converter we should enable this test
    @Test
    public void should_returnRecordsAndConversionStatus_whenNestedArrayOfPropertiesProvidedWithInvalidValues() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithNestedArrayOfPropertiesAndInvalidValues(1, recordId, KIND, LEGAL_TAG, UNIT_PERSISTABLE_REFERENCE, "Unit");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 12);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),"");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(1, responseObject.conversionStatuses.size());
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(2, responseObject.records[0].data.size());
        List<DummyRecordsHelper.RecordStatusMock> conversionStatuses = responseObject.conversionStatuses;
        assertEquals("ERROR", conversionStatuses.get(0).status);
        assertEquals("Unit conversion: illegal value for property markers[1].measuredDepth", conversionStatuses.get(0).errors.get(0));

        ClientResponse deleteResponse = TestUtils.send("records/" + recordId + 12, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse.getStatus());
    }

    @Ignore // Ignoring the test for now, once we have CRS converter we should enable this test
    @Test
    public void should_returnRecordsAndConversionStatus_whenInhomogeneousNestedArrayOfPropertiesProvidedWithoutError() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithInhomogeneousNestedArrayOfProperties(1, recordId, KIND, LEGAL_TAG, UNIT_PERSISTABLE_REFERENCE, "Unit");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 13);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),"");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(1, responseObject.conversionStatuses.size());
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(2, responseObject.records[0].data.size());
        List<DummyRecordsHelper.RecordStatusMock> conversionStatuses = responseObject.conversionStatuses;
        assertEquals("SUCCESS", conversionStatuses.get(0).status);

        ClientResponse deleteResponse = TestUtils.send("records/" + recordId + 13, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse.getStatus());
    }

    @Ignore // Ignoring the test for now, once we have CRS converter we should enable this test
    @Test
    public void should_returnRecordsAndConversionStatus_whenInhomogeneousNestedArrayOfPropertiesProvidedWithInvalidValues() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithInhomogeneousNestedArrayOfPropertiesAndInvalidValues(1, recordId, KIND, LEGAL_TAG, UNIT_PERSISTABLE_REFERENCE, "Unit");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 13);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),"");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(1, responseObject.conversionStatuses.size());
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(2, responseObject.records[0].data.size());
        List<DummyRecordsHelper.RecordStatusMock> conversionStatuses = responseObject.conversionStatuses;
        assertEquals("ERROR", conversionStatuses.get(0).status);
        assertEquals("Unit conversion: illegal value for property markers[1].measuredDepth", conversionStatuses.get(0).errors.get(0));

        ClientResponse deleteResponse = TestUtils.send("records/" + recordId + 13, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse.getStatus());
    }

    @Ignore // Ignoring the test for now, once we have CRS converter we should enable this test
    @Test
    public void should_returnRecordsAndConversionStatus_whenInhomogeneousNestedArrayOfPropertiesProvidedWithIndexOutOfBoundary() throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID().toString();
        String jsonInput = RecordUtil.createJsonRecordWithInhomogeneousNestedArrayOfPropertiesAndIndexOutOfBoundary(1, recordId, KIND, LEGAL_TAG, UNIT_PERSISTABLE_REFERENCE, "Unit");
        ClientResponse createResponse = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
        assertEquals(201, createResponse.getStatus());

        JsonArray records = new JsonArray();
        records.add(recordId + 13);

        JsonObject body = new JsonObject();
        body.add("records", records);

        Map<String, String> headers = HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken());
        headers.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");
        ClientResponse response = TestUtils.send("query/records:batch", "POST", headers, body.toString(),"");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        DummyRecordsHelper.ConvertedRecordsMock responseObject = RECORDS_HELPER.getConvertedRecordsMockFromResponse(response);
        assertEquals(1, responseObject.records.length);
        assertEquals(0, responseObject.notFound.length);
        assertEquals(1, responseObject.conversionStatuses.size());
        assertEquals(TestUtils.getAcl(), responseObject.records[0].acl.viewers[0]);
        assertEquals(KIND, responseObject.records[0].kind);
        assertTrue(responseObject.records[0].version != null && !responseObject.records[0].version.isEmpty());
        assertEquals(2, responseObject.records[0].data.size());
        List<DummyRecordsHelper.RecordStatusMock> conversionStatuses = responseObject.conversionStatuses;
        assertEquals("SUCCESS", conversionStatuses.get(0).status);
        assertEquals("Unit conversion: property markers[2].measuredDepth missing", conversionStatuses.get(0).errors.get(0));

        ClientResponse deleteResponse = TestUtils.send("records/" + recordId + 13, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertEquals(204, deleteResponse.getStatus());
    }
}
