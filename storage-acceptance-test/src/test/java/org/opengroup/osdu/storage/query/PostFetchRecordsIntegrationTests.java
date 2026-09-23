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

import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;

import org.opengroup.osdu.core.test.client.model.storage.ConvertedRecords;
import org.opengroup.osdu.core.test.client.model.storage.QueryRecordsRequest;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.RecordStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.storage.records.BaseRecordsAcceptanceTest;
import org.opengroup.osdu.storage.util.RecordUtil;


public final class PostFetchRecordsIntegrationTests extends BaseRecordsAcceptanceTest {

    private static final long NOW = System.currentTimeMillis();

    private static String RECORD_ID_PREFIX;
    private static String KIND;
    private static String LEGAL_TAG;
    private static final String PERSISTABLE_REFERENCE = "%7B%22LB_CRS%22%3A%22%257B%2522WKT%2522%253A%2522PROJCS%255B%255C%2522British_National_Grid%255C%2522%252CGEOGCS%255B%255C%2522GCS_OSGB_1936%255C%2522%252CDATUM%255B%255C%2522D_OSGB_1936%255C%2522%252CSPHEROID%255B%255C%2522Airy_1830%255C%2522%252C6377563.396%252C299.3249646%255D%255D%252CPRIMEM%255B%255C%2522Greenwich%255C%2522%252C0.0%255D%252CUNIT%255B%255C%2522Degree%255C%2522%252C0.0174532925199433%255D%255D%252CPROJECTION%255B%255C%2522Transverse_Mercator%255C%2522%255D%252CPARAMETER%255B%255C%2522False_Easting%255C%2522%252C400000.0%255D%252CPARAMETER%255B%255C%2522False_Northing%255C%2522%252C-100000.0%255D%252CPARAMETER%255B%255C%2522Central_Meridian%255C%2522%252C-2.0%255D%252CPARAMETER%255B%255C%2522Scale_Factor%255C%2522%252C0.9996012717%255D%252CPARAMETER%255B%255C%2522Latitude_Of_Origin%255C%2522%252C49.0%255D%252CUNIT%255B%255C%2522Meter%255C%2522%252C1.0%255D%252CAUTHORITY%255B%255C%2522EPSG%255C%2522%252C27700%255D%255D%2522%252C%2522Type%2522%253A%2522LBCRS%2522%252C%2522EngineVersion%2522%253A%2522PE_10_3_1%2522%252C%2522AuthorityCode%2522%253A%257B%2522Authority%2522%253A%2522EPSG%2522%252C%2522Code%2522%253A%252227700%2522%257D%252C%2522Name%2522%253A%2522British_National_Grid%2522%257D%22%2C%22TRF%22%3A%22%257B%2522WKT%2522%253A%2522GEOGTRAN%255B%255C%2522OSGB_1936_To_WGS_1984_Petroleum%255C%2522%252CGEOGCS%255B%255C%2522GCS_OSGB_1936%255C%2522%252CDATUM%255B%255C%2522D_OSGB_1936%255C%2522%252CSPHEROID%255B%255C%2522Airy_1830%255C%2522%252C6377563.396%252C299.3249646%255D%255D%252CPRIMEM%255B%255C%2522Greenwich%255C%2522%252C0.0%255D%252CUNIT%255B%255C%2522Degree%255C%2522%252C0.0174532925199433%255D%255D%252CGEOGCS%255B%255C%2522GCS_WGS_1984%255C%2522%252CDATUM%255B%255C%2522D_WGS_1984%255C%2522%252CSPHEROID%255B%255C%2522WGS_1984%255C%2522%252C6378137.0%252C298.257223563%255D%255D%252CPRIMEM%255B%255C%2522Greenwich%255C%2522%252C0.0%255D%252CUNIT%255B%255C%2522Degree%255C%2522%252C0.0174532925199433%255D%255D%252CMETHOD%255B%255C%2522Position_Vector%255C%2522%255D%252CPARAMETER%255B%255C%2522X_Axis_Translation%255C%2522%252C446.448%255D%252CPARAMETER%255B%255C%2522Y_Axis_Translation%255C%2522%252C-125.157%255D%252CPARAMETER%255B%255C%2522Z_Axis_Translation%255C%2522%252C542.06%255D%252CPARAMETER%255B%255C%2522X_Axis_Rotation%255C%2522%252C0.15%255D%252CPARAMETER%255B%255C%2522Y_Axis_Rotation%255C%2522%252C0.247%255D%252CPARAMETER%255B%255C%2522Z_Axis_Rotation%255C%2522%252C0.842%255D%252CPARAMETER%255B%255C%2522Scale_Difference%255C%2522%252C-20.489%255D%252CAUTHORITY%255B%255C%2522EPSG%255C%2522%252C1314%255D%255D%2522%252C%2522Type%2522%253A%2522STRF%2522%252C%2522EngineVersion%2522%253A%2522PE_10_3_1%2522%252C%2522AuthorityCode%2522%253A%257B%2522Authority%2522%253A%2522EPSG%2522%252C%2522Code%2522%253A%25221314%2522%257D%252C%2522Name%2522%253A%2522OSGB_1936_To_WGS_1984_Petroleum%2522%257D%22%2C%22Type%22%3A%22EBCRS%22%2C%22EngineVersion%22%3A%22PE_10_3_1%22%2C%22Name%22%3A%22OSGB+1936+*+UKOOA-Pet+%2F+British+National+Grid+%5B27700%2C1314%5D%22%2C%22AuthorityCode%22%3A%7B%22Authority%22%3A%22MyCompany%22%2C%22Code%22%3A%2227700006%22%7D%7D";
    private static final String PERSISTABLE_REFERENCE_CRS = "{\"lateBoundCRS\":{\"wkt\":\"PROJCS[\\\"ED_1950_UTM_Zone_32N\\\",GEOGCS[\\\"GCS_European_1950\\\",DATUM[\\\"D_European_1950\\\",SPHEROID[\\\"International_1924\\\",6378388.0,297.0]],PRIMEM[\\\"Greenwich\\\",0.0],UNIT[\\\"Degree\\\",0.0174532925199433]],PROJECTION[\\\"Transverse_Mercator\\\"],PARAMETER[\\\"False_Easting\\\",500000.0],PARAMETER[\\\"False_Northing\\\",0.0],PARAMETER[\\\"Central_Meridian\\\",9.0],PARAMETER[\\\"Scale_Factor\\\",0.9996],PARAMETER[\\\"Latitude_Of_Origin\\\",0.0],UNIT[\\\"Meter\\\",1.0],AUTHORITY[\\\"EPSG\\\",23032]]\",\"ver\":\"PE_10_3_1\",\"name\":\"ED_1950_UTM_Zone_32N\",\"authCode\":{\"auth\":\"EPSG\",\"code\":\"23032\"},\"type\":\"LBC\"},\"singleCT\":{\"wkt\":\"GEOGTRAN[\\\"ED_1950_To_WGS_1984_23\\\",GEOGCS[\\\"GCS_European_1950\\\",DATUM[\\\"D_European_1950\\\",SPHEROID[\\\"International_1924\\\",6378388.0,297.0]],PRIMEM[\\\"Greenwich\\\",0.0],UNIT[\\\"Degree\\\",0.0174532925199433]],GEOGCS[\\\"GCS_WGS_1984\\\",DATUM[\\\"D_WGS_1984\\\",SPHEROID[\\\"WGS_1984\\\",6378137.0,298.257223563]],PRIMEM[\\\"Greenwich\\\",0.0],UNIT[\\\"Degree\\\",0.0174532925199433]],METHOD[\\\"Position_Vector\\\"],PARAMETER[\\\"X_Axis_Translation\\\",-116.641],PARAMETER[\\\"Y_Axis_Translation\\\",-56.931],PARAMETER[\\\"Z_Axis_Translation\\\",-110.559],PARAMETER[\\\"X_Axis_Rotation\\\",0.893],PARAMETER[\\\"Y_Axis_Rotation\\\",0.921],PARAMETER[\\\"Z_Axis_Rotation\\\",-0.917],PARAMETER[\\\"Scale_Difference\\\",-3.52],AUTHORITY[\\\"EPSG\\\",1612]]\",\"ver\":\"PE_10_3_1\",\"name\":\"ED_1950_To_WGS_1984_23\",\"authCode\":{\"auth\":\"EPSG\",\"code\":\"1612\"},\"type\":\"ST\"},\"ver\":\"PE_10_3_1\",\"name\":\"ED50 * EPSG-Nor N62 2001 / UTM zone 32N [23032,1612]\",\"authCode\":{\"auth\":\"SLB\",\"code\":\"23032023\"},\"type\":\"EBC\"}";
    private static final String PERSISTABLE_REFERENCE_UNIT_Z = "{\"baseMeasurement\":{\"ancestry\":\"Length\",\"type\":\"UM\"},\"scaleOffset\":{\"offset\":0.0,\"scale\":0.3048},\"symbol\":\"ft\",\"type\":\"USO\"}";
    private static final String DATETIME_PERSISTABLE_REFERENCE = "{\"type\":\"DAT\",\"format\":\"YYYY-MM-DD\"}";
    private static final String UNIT_PERSISTABLE_REFERENCE = "{\"abcd\":{\"a\":0.0,\"b\":0.3048,\"c\":1.0,\"d\":0.0},\"symbol\":\"ft\",\"baseMeasurement\":{\"ancestry\":\"L\",\"type\":\"UM\"},\"type\":\"UAD\"}";
    private static String UNIT_OF_MEASURE_ID;
    private static final String FRAME_OF_REFERENCE_NAME = "frame-of-reference";
    private static final String FRAME_OF_REFERENCE_VAL = "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;";


    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        RECORD_ID_PREFIX = getTenantId() + ":query:";
        KIND = getTenantId() + ":ds:query:1.0." + NOW;
        LEGAL_TAG = getTenantId() + "-storage-" + NOW;
        UNIT_OF_MEASURE_ID = String.format("%s:reference-data--UnitOfMeasure:ft:", getTenantId());
        createLegalTag(LEGAL_TAG);
    }

    @Test
    public void should_returnSingleRecordMatching_when_noConversionRequired() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsWithReference(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS"));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, "none"));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(0, responseObject.conversionStatuses().size());

        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(recordId + 0, responseObject.records()[0].id());
        assertEquals(3, responseObject.records()[0].data().size());
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

    @Test
    public void should_returnRecordMatchingAndRecordNotFound_when_noConversionRequired() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsWithReference(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS"));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
        String notExistingId = RECORD_ID_PREFIX + "nonexisting:id";

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0, notExistingId), Map.of(FRAME_OF_REFERENCE_NAME, "none"));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(1, responseObject.notFound().length);
        assertEquals(0, responseObject.conversionStatuses().size());

        assertEquals(notExistingId, responseObject.notFound()[0]);
        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(recordId + 0, responseObject.records()[0].id());
        assertEquals(3, responseObject.records()[0].data().size());
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

    @Test
    public void should_return400BadRequest_when_moreThan20RecordsRequiredAndNoConversionRequired() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();

        String[] recordIds = new String[21];
        for (int i = 0; i < 21; i++) {
            recordIds[i] = recordId + i;
        }

        ClientException ex = assertThrows(ClientException.class,
            () -> storageClient.queryRecordsBatchPost(
                new QueryRecordsRequest(recordIds, null),
                Map.of(FRAME_OF_REFERENCE_NAME, "none")));
        assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void should_returnConvertedRecords_whenConversionRequiredAndNoError() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithReference(2, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0, recordId + 1), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(2, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(2, responseObject.conversionStatuses().size());

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(3, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
        var deleteResponse2 = storageClient.deleteRecord(recordId + 1);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse2.statusCode());

    }

    @Test
    public void should_returnConvertedRecords_whenConversionRequiredAndNoErrorWithMultiplePairOfCoordinates() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithMultiplePairOfCoordinates(2, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0, recordId + 1), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(2, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(5, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
        var deleteResponse2 = storageClient.deleteRecord(recordId + 1);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse2.statusCode());

    }

    @Test
    public void should_returnOriginalRecordsAndConversionStatusAsNoMeta_whenConversionRequiredAndNoMetaBlockInRecord() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsNoMetaBlock(2, recordId, KIND, LEGAL_TAG);
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
        String notExistingId = RECORD_ID_PREFIX + "nonexisting:id";

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0, recordId + 1, notExistingId), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(2, responseObject.records().length);
        assertEquals(1, responseObject.notFound().length);
        assertEquals(2, responseObject.conversionStatuses().size());
        assertEquals(notExistingId, responseObject.notFound()[0]);
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(3, responseObject.records()[0].data().size());
        List<RecordStatus> conversionStatuses = responseObject.conversionStatuses();
        assertEquals("CRS Conversion: Meta Block is missing or empty in this record, no conversion applied.", conversionStatuses.get(0).errors().get(0));
        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
        var deleteResponse2 = storageClient.deleteRecord(recordId + 1);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse2.statusCode());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndConversionErrorExists() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsMissingValue(2, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS"));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0, recordId + 1), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(2, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(2, responseObject.conversionStatuses().size());
        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(2, responseObject.records()[0].data().size());
        List<RecordStatus> conversionStatuses = responseObject.conversionStatuses();
        assertEquals("CRS conversion: Unknown coordinate pair 'z'.", conversionStatuses.get(0).errors().get(1));
        assertEquals("CRS conversion: property 'Y' is missing in datablock, no conversion applied to this property and its corresponding pairing property.", conversionStatuses.get(0).errors().get(0));
        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
        var deleteResponse2 = storageClient.deleteRecord(recordId + 1);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse2.statusCode());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndNestedPropertyProvidedInMetaBlock() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsWithNestedProperty(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS"));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndNestedPropertyProvidedInMetaBlock1() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsWithNestedProperty(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE, "CRS"));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenDateAndFormatProvidedInMetaBlock() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsWithDateFormat(1, recordId, KIND, LEGAL_TAG,
            "creationDate", "2019-08-03", DATETIME_PERSISTABLE_REFERENCE));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);

        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenNestedArrayOfPropertiesProvidedWithoutError() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();

        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsWithNestedArrayOfProperties(1, recordId, KIND, LEGAL_TAG, UNIT_PERSISTABLE_REFERENCE, "Unit",  UNIT_OF_MEASURE_ID));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 12), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertNotNull(responseObject.records());
        assertEquals(1, responseObject.records().length);
        assertNotNull(responseObject.notFound());
        assertEquals(0, responseObject.notFound().length);
        assertNotNull(responseObject.conversionStatuses());
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(2, responseObject.records()[0].data().size());
        List<RecordStatus> conversionStatuses = responseObject.conversionStatuses();
        assertEquals("SUCCESS", conversionStatuses.get(0).status());

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId + 12);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenNestedArrayOfPropertiesProvidedWithInvalidValues() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsWithNestedArrayOfPropertiesAndInvalidValues(1, recordId, KIND, LEGAL_TAG, UNIT_PERSISTABLE_REFERENCE, "Unit", UNIT_OF_MEASURE_ID));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 12), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(2, responseObject.records()[0].data().size());
        List<RecordStatus> conversionStatuses = responseObject.conversionStatuses();
        assertEquals("ERROR", conversionStatuses.get(0).status());
        assertEquals("Unit conversion: illegal value for property markers[1].measuredDepth", conversionStatuses.get(0).errors().get(0));

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId + 12);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenInhomogeneousNestedArrayOfPropertiesProvidedWithoutError() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsWithInhomogeneousNestedArrayOfProperties(1, recordId, KIND, LEGAL_TAG, UNIT_PERSISTABLE_REFERENCE, "Unit", UNIT_OF_MEASURE_ID));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 13), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(2, responseObject.records()[0].data().size());
        List<RecordStatus> conversionStatuses = responseObject.conversionStatuses();
        assertEquals("SUCCESS", conversionStatuses.get(0).status());

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId + 13);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenInhomogeneousNestedArrayOfPropertiesProvidedWithInvalidValues() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsWithInhomogeneousNestedArrayOfPropertiesAndInvalidValues(1, recordId, KIND, LEGAL_TAG, UNIT_PERSISTABLE_REFERENCE, "Unit", UNIT_OF_MEASURE_ID));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 13), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(2, responseObject.records()[0].data().size());
        List<RecordStatus> conversionStatuses = responseObject.conversionStatuses();
        assertEquals("ERROR", conversionStatuses.get(0).status());
        assertEquals("Unit conversion: illegal value for property markers[1].measuredDepth", conversionStatuses.get(0).errors().get(0));

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId + 13);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

    @Test
    public void should_returnRecordsAndConversionStatus_whenInhomogeneousNestedArrayOfPropertiesProvidedWithIndexOutOfBoundary() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = withTestAcl(RecordUtil.createRecordsWithInhomogeneousNestedArrayOfPropertiesAndIndexOutOfBoundary(1, recordId, KIND, LEGAL_TAG, UNIT_PERSISTABLE_REFERENCE, "Unit", UNIT_OF_MEASURE_ID));
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 13), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals(getAcl(), responseObject.records()[0].acl().viewers()[0]);
        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(2, responseObject.records()[0].data().size());
        List<RecordStatus> conversionStatuses = responseObject.conversionStatuses();
        assertEquals("SUCCESS", conversionStatuses.get(0).status());
        assertEquals("Unit conversion: property markers[2].measuredDepth missing", conversionStatuses.get(0).errors().get(0));

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId + 13);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

    @Test
    public void should_returnRecordsAfterCrsConversion__whenProvidedRecordWithAsIngestedCoordinatesBlockTypePoint() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithAsIngestedCoordinates(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE_CRS, PERSISTABLE_REFERENCE_UNIT_Z, "AnyCrsPoint", "SpatialLocation");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals("SUCCESS", responseObject.conversionStatuses().get(0).status());

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(1, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
    }

    @Test
    public void should_returnRecordsAfterCrsConversion__whenProvidedRecordWithAsIngestedCoordinatesBlockTypeMultiPoint() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithAsIngestedCoordinates(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE_CRS, PERSISTABLE_REFERENCE_UNIT_Z, "AnyCrsMultiPoint", "SpatialLocation");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals("SUCCESS", responseObject.conversionStatuses().get(0).status());

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(1, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
    }

    @Test
    public void should_returnRecordsAfterCrsConversion__whenProvidedRecordWithAsIngestedCoordinatesBlockTypePolygon() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithAsIngestedCoordinates(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE_CRS, PERSISTABLE_REFERENCE_UNIT_Z, "AnyCrsPolygon", "SpatialLocation");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals("SUCCESS", responseObject.conversionStatuses().get(0).status());

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(1, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
    }

    @Test
    public void should_returnRecordsAfterCrsConversion__whenProvidedRecordWithAsIngestedCoordinatesBlockTypeMultiPolygon() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithAsIngestedCoordinates(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE_CRS, PERSISTABLE_REFERENCE_UNIT_Z, "AnyCrsMultiPolygon", "SpatialLocation");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals("SUCCESS", responseObject.conversionStatuses().get(0).status());

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(1, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
    }

    @Test
    public void should_returnRecordsAfterCrsConversion__whenProvidedRecordWithAsIngestedCoordinatesBlockTypeLineString() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithAsIngestedCoordinates(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE_CRS, PERSISTABLE_REFERENCE_UNIT_Z, "AnyCrsLineString", "SpatialLocation");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals("SUCCESS", responseObject.conversionStatuses().get(0).status());

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(1, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
    }

    @Test
    public void should_returnRecordsAfterCrsConversion__whenProvidedRecordWithAsIngestedCoordinatesBlockTypeMultiLineString() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithAsIngestedCoordinates(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE_CRS, PERSISTABLE_REFERENCE_UNIT_Z, "AnyCrsMultiLineString", "SpatialLocation");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals("SUCCESS", responseObject.conversionStatuses().get(0).status());

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(1, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
    }

    @Test
    public void should_returnRecordsAfterCrsConversion__whenProvidedRecordWithAsIngestedCoordinatesBlockTypeGeometryCollection() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithAsIngestedCoordinates(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE_CRS, PERSISTABLE_REFERENCE_UNIT_Z, "AnyCrsGeometryCollection", "SpatialLocation");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals("SUCCESS", responseObject.conversionStatuses().get(0).status());

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(1, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
    }

    @Test
    public void should_returnConvertedRecords_whenConversionRequiredWithAsIngestedCoordinatesBlockWithError() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithInvalidAsIngestedCoordinates(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE_CRS, PERSISTABLE_REFERENCE_UNIT_Z, "AnyCrsPoint", "SpatialLocation");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals("CRS conversion: 'features' missing, no conversion applied.", responseObject.conversionStatuses().get(0).errors().get(0));

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(1, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
    }

    @Test
    public void should_returnConvertedRecords_whenConversionNotRequiredWithAsIngestedCoordinatesAndWgs84CoordinatesBlocks() {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordsWithWGS84Coordinates(1, recordId, KIND, LEGAL_TAG, PERSISTABLE_REFERENCE_CRS, PERSISTABLE_REFERENCE_UNIT_Z, "AnyCrsPoint", "SpatialLocation");
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());

        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId + 0), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));

        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());

        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.records().length);
        assertEquals(0, responseObject.notFound().length);
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals("CRS conversion: 'Wgs84Coordinates' block exists, no conversion applied.", responseObject.conversionStatuses().get(0).errors().get(0));

        assertEquals(KIND, responseObject.records()[0].kind());
        assertTrue(responseObject.records()[0].version() != null && !responseObject.records()[0].version().isEmpty());
        assertEquals(1, responseObject.records()[0].data().size());

        var deleteResponse1 = storageClient.deleteRecord(recordId + 0);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse1.statusCode());
    }

    @Test
    public void should_returnConvertedRecord_withPrimitiveArrayAndConfiguredUnitOfMeasure()
        throws Exception {
        String recordId = RECORD_ID_PREFIX + UUID.randomUUID();
        StorageRecord[] jsonInput = RecordUtil.createRecordForUnitConversionWithPrimitiveArray(
            recordId,
            KIND,
            LEGAL_TAG,
            UNIT_OF_MEASURE_ID,
            "records/primitive-array-unit-conversion.json"
        );
        HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(jsonInput);
        assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
        var fetchResponse = storageClient.queryRecordsBatchPost(QueryRecordsRequest.of(recordId), Map.of(FRAME_OF_REFERENCE_NAME, FRAME_OF_REFERENCE_VAL));
        assertEquals(HttpStatus.SC_OK, fetchResponse.statusCode());
        ConvertedRecords responseObject = fetchResponse.body();
        assertEquals(1, responseObject.conversionStatuses().size());
        assertEquals("SUCCESS", responseObject.conversionStatuses().get(0).status());

        HttpResponse<Void> deleteResponse = storageClient.deleteRecord(recordId);
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }

}
