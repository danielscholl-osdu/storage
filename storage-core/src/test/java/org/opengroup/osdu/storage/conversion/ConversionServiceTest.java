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

package org.opengroup.osdu.storage.conversion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.storage.ConversionStatus;
import org.opengroup.osdu.core.common.model.crs.RecordsAndStatuses;
import org.opengroup.osdu.core.common.model.crs.ConvertStatus;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConversionServiceTest {

    @Mock
    private CrsConversionService crsConversionService;

    @Mock
    private JaxRsDpsLog logger;

    @InjectMocks
    private DpsConversionService sut;

    private JsonParser jsonParser = new JsonParser();
    private List<JsonObject> originalRecords = new ArrayList<>();
    private static final String RECORD_1 = "{\"id\":\"unit-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_2 = "{\"id\":\"unit-test-2\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}";
    private static final String RECORD_3 = "{\"id\":\"unit-test-3\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String CONVERTED_RECORD_1 = "{\"id\":\"unit-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":15788.036,\"Y\":9567.40,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String CONVERTED_RECORD_3 = "{\"id\":\"unit-test-3\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":15788.036,\"Y\":9567.40,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String GEO_JSON_RECORD_1 = "{\"id\":\"geo-json-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"geo-json-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0,\"SpatialLocation\":{\"AsIngestedCoordinates\":{}}}}";
    private static final String GEO_JSON_RECORD_2 = "{\"id\":\"geo-json-test-2\",\"kind\":\"geo-json:test:2.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0,\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\": {\"coordinates\": [[[[30,20],[45,40],[10,40],[30,20]]],[[[15,5],[40,10],[10,20],[5,10],[15,5]]]],\"bbox\": null,\"type\": \"AnyCrsMultiPolygon\"},\"bbox\": null,\"properties\": {},\"type\": \"AnyCrsFeature\"}],\"bbox\": null,\"properties\":{},\"persistableReferenceCrs\": \"{\\\"lateBoundCRS\\\":\\\"wkt\\\":\\\"PROJCS[\\\\\\\"ED_1950_UTM_Zone_32N\\\\\\\",GEOGCS[\\\\\\\"GCS_European_1950\\\\\\\",DATUM[\\\\\\\"D_European_1950\\\\\\\",SPHEROID[\\\\\\\"International_1924\\\\\\\",6378388.0,297.0]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],PROJECTION[\\\\\\\"Transverse_Mercator\\\\\\\"],PARAMETER[\\\\\\\"False_Easting\\\\\\\",500000.0],PARAMETER[\\\\\\\"False_Northing\\\\\\\",0.0],PARAMETER[\\\\\\\"Central_Meridian\\\\\\\",9.0],PARAMETER[\\\\\\\"Scale_Factor\\\\\\\",0.9996],PARAMETER[\\\\\\\"Latitude_Of_Origin\\\\\\\",0.0],UNIT[\\\\\\\"Meter\\\\\\\",1.0],AUTHORITY[\\\\\\\"EPSG\\\\\\\",23032]]\\\",\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED_1950_UTM_Zone_32N\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"EPSG\\\",\\\"code\\\":\\\"23032\\\"},\\\"type\\\":\\\"LBC\\\"},\\\"singleCT\\\":{\\\"wkt\\\":\\\"GEOGTRAN[\\\\\\\"ED_1950_To_WGS_1984_23\\\\\\\",GEOGCS[\\\\\\\"GCS_European_1950\\\\\\\",DATUM[\\\\\\\"D_European_1950\\\\\\\",SPHEROID[\\\\\\\"International_1924\\\\\\\",6378388.0,297.0]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],GEOGCS[\\\\\\\"GCS_WGS_1984\\\\\\\",DATUM[\\\\\\\"D_WGS_1984\\\\\\\",SPHEROID[\\\\\\\"WGS_1984\\\\\\\",6378137.0,298.257223563]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],METHOD[\\\\\\\"Position_Vector\\\\\\\"],PARAMETER[\\\\\\\"X_Axis_Translation\\\\\\\",-116.641],PARAMETER[\\\\\\\"Y_Axis_Translation\\\\\\\",-56.931],PARAMETER[\\\\\\\"Z_Axis_Translation\\\\\\\",-110.559],PARAMETER[\\\\\\\"X_Axis_Rotation\\\\\\\",0.893],PARAMETER[\\\\\\\"Y_Axis_Rotation\\\\\\\",0.921],PARAMETER[\\\\\\\"Z_Axis_Rotation\\\\\\\",-0.917],PARAMETER[\\\\\\\"Scale_Difference\\\\\\\",-3.52],AUTHORITY[\\\\\\\"EPSG\\\\\\\",1612]]\\\",\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED_1950_To_WGS_1984_23\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"EPSG\\\",\\\"code\\\":\\\"1612\\\"},\\\"type\\\":\\\"ST\\\"},\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED50 * EPSG-Nor N62 2001 / UTM zone 32N [23032,1612]\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"SLB\\\",\\\"code\\\":\\\"23032023\\\"},\\\"type\\\":\\\"EBC\\\"}\",\"persistableReferenceUnitZ\": \"{\\\"baseMeasurement\\\":{\\\"ancestry\\\":\\\"Length\\\",\\\"type\\\":\\\"UM\\\"},\\\"scaleOffset\\\":{\\\"offset\\\":0.0,\\\"scale\\\":0.3048},\\\"symbol\\\":\\\"ft\\\",\\\"type\\\":\\\"USO\\\"}\",\"type\": \"AnyCrsFeatureCollection\"}}}";
    private static final String GEO_JSON_RECORD_3 = "{\"id\":\"geo-json-test-2\",\"kind\":\"geo-json:test:2.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0,\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\": {\"coordinates\": [313405.9477893702,6544797.620047403,6.56167],\"bbox\": null,\"type\": \"AnyCrsPoint\"},\"bbox\": null,\"properties\": {},\"type\": \"AnyCrsFeature\"}],\"bbox\": null,\"properties\":{},\"persistableReferenceCrs\": \"{\\\"lateBoundCRS\\\":\\\"wkt\\\":\\\"PROJCS[\\\\\\\"ED_1950_UTM_Zone_32N\\\\\\\",GEOGCS[\\\\\\\"GCS_European_1950\\\\\\\",DATUM[\\\\\\\"D_European_1950\\\\\\\",SPHEROID[\\\\\\\"International_1924\\\\\\\",6378388.0,297.0]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],PROJECTION[\\\\\\\"Transverse_Mercator\\\\\\\"],PARAMETER[\\\\\\\"False_Easting\\\\\\\",500000.0],PARAMETER[\\\\\\\"False_Northing\\\\\\\",0.0],PARAMETER[\\\\\\\"Central_Meridian\\\\\\\",9.0],PARAMETER[\\\\\\\"Scale_Factor\\\\\\\",0.9996],PARAMETER[\\\\\\\"Latitude_Of_Origin\\\\\\\",0.0],UNIT[\\\\\\\"Meter\\\\\\\",1.0],AUTHORITY[\\\\\\\"EPSG\\\\\\\",23032]]\\\",\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED_1950_UTM_Zone_32N\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"EPSG\\\",\\\"code\\\":\\\"23032\\\"},\\\"type\\\":\\\"LBC\\\"},\\\"singleCT\\\":{\\\"wkt\\\":\\\"GEOGTRAN[\\\\\\\"ED_1950_To_WGS_1984_23\\\\\\\",GEOGCS[\\\\\\\"GCS_European_1950\\\\\\\",DATUM[\\\\\\\"D_European_1950\\\\\\\",SPHEROID[\\\\\\\"International_1924\\\\\\\",6378388.0,297.0]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],GEOGCS[\\\\\\\"GCS_WGS_1984\\\\\\\",DATUM[\\\\\\\"D_WGS_1984\\\\\\\",SPHEROID[\\\\\\\"WGS_1984\\\\\\\",6378137.0,298.257223563]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],METHOD[\\\\\\\"Position_Vector\\\\\\\"],PARAMETER[\\\\\\\"X_Axis_Translation\\\\\\\",-116.641],PARAMETER[\\\\\\\"Y_Axis_Translation\\\\\\\",-56.931],PARAMETER[\\\\\\\"Z_Axis_Translation\\\\\\\",-110.559],PARAMETER[\\\\\\\"X_Axis_Rotation\\\\\\\",0.893],PARAMETER[\\\\\\\"Y_Axis_Rotation\\\\\\\",0.921],PARAMETER[\\\\\\\"Z_Axis_Rotation\\\\\\\",-0.917],PARAMETER[\\\\\\\"Scale_Difference\\\\\\\",-3.52],AUTHORITY[\\\\\\\"EPSG\\\\\\\",1612]]\\\",\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED_1950_To_WGS_1984_23\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"EPSG\\\",\\\"code\\\":\\\"1612\\\"},\\\"type\\\":\\\"ST\\\"},\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED50 * EPSG-Nor N62 2001 / UTM zone 32N [23032,1612]\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"SLB\\\",\\\"code\\\":\\\"23032023\\\"},\\\"type\\\":\\\"EBC\\\"}\",\"persistableReferenceUnitZ\": \"{\\\"baseMeasurement\\\":{\\\"ancestry\\\":\\\"Length\\\",\\\"type\\\":\\\"UM\\\"},\\\"scaleOffset\\\":{\\\"offset\\\":0.0,\\\"scale\\\":0.3048},\\\"symbol\\\":\\\"ft\\\",\\\"type\\\":\\\"USO\\\"}\",\"type\": \"AnyCrsFeatureCollection\"}}}";
    private static final String GEO_JSON_CONVERTED_RECORD_1 = "{\"id\":\"geo-json-test-2\",\"kind\":\"geo-json:test:2.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0,\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\": {\"coordinates\": [[[[30,20],[45,40],[10,40],[30,20]]],[[[15,5],[40,10],[10,20],[5,10],[15,5]]]],\"bbox\": null,\"type\": \"AnyCrsMultiPolygon\"},\"bbox\": null,\"properties\": {},\"type\": \"AnyCrsFeature\"}],\"bbox\": null,\"properties\":{},\"persistableReferenceCrs\": \"{\\\"lateBoundCRS\\\":\\\"wkt\\\":\\\"PROJCS[\\\\\\\"ED_1950_UTM_Zone_32N\\\\\\\",GEOGCS[\\\\\\\"GCS_European_1950\\\\\\\",DATUM[\\\\\\\"D_European_1950\\\\\\\",SPHEROID[\\\\\\\"International_1924\\\\\\\",6378388.0,297.0]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],PROJECTION[\\\\\\\"Transverse_Mercator\\\\\\\"],PARAMETER[\\\\\\\"False_Easting\\\\\\\",500000.0],PARAMETER[\\\\\\\"False_Northing\\\\\\\",0.0],PARAMETER[\\\\\\\"Central_Meridian\\\\\\\",9.0],PARAMETER[\\\\\\\"Scale_Factor\\\\\\\",0.9996],PARAMETER[\\\\\\\"Latitude_Of_Origin\\\\\\\",0.0],UNIT[\\\\\\\"Meter\\\\\\\",1.0],AUTHORITY[\\\\\\\"EPSG\\\\\\\",23032]]\\\",\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED_1950_UTM_Zone_32N\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"EPSG\\\",\\\"code\\\":\\\"23032\\\"},\\\"type\\\":\\\"LBC\\\"},\\\"singleCT\\\":{\\\"wkt\\\":\\\"GEOGTRAN[\\\\\\\"ED_1950_To_WGS_1984_23\\\\\\\",GEOGCS[\\\\\\\"GCS_European_1950\\\\\\\",DATUM[\\\\\\\"D_European_1950\\\\\\\",SPHEROID[\\\\\\\"International_1924\\\\\\\",6378388.0,297.0]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],GEOGCS[\\\\\\\"GCS_WGS_1984\\\\\\\",DATUM[\\\\\\\"D_WGS_1984\\\\\\\",SPHEROID[\\\\\\\"WGS_1984\\\\\\\",6378137.0,298.257223563]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],METHOD[\\\\\\\"Position_Vector\\\\\\\"],PARAMETER[\\\\\\\"X_Axis_Translation\\\\\\\",-116.641],PARAMETER[\\\\\\\"Y_Axis_Translation\\\\\\\",-56.931],PARAMETER[\\\\\\\"Z_Axis_Translation\\\\\\\",-110.559],PARAMETER[\\\\\\\"X_Axis_Rotation\\\\\\\",0.893],PARAMETER[\\\\\\\"Y_Axis_Rotation\\\\\\\",0.921],PARAMETER[\\\\\\\"Z_Axis_Rotation\\\\\\\",-0.917],PARAMETER[\\\\\\\"Scale_Difference\\\\\\\",-3.52],AUTHORITY[\\\\\\\"EPSG\\\\\\\",1612]]\\\",\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED_1950_To_WGS_1984_23\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"EPSG\\\",\\\"code\\\":\\\"1612\\\"},\\\"type\\\":\\\"ST\\\"},\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED50 * EPSG-Nor N62 2001 / UTM zone 32N [23032,1612]\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"SLB\\\",\\\"code\\\":\\\"23032023\\\"},\\\"type\\\":\\\"EBC\\\"}\",\"persistableReferenceUnitZ\": \"{\\\"baseMeasurement\\\":{\\\"ancestry\\\":\\\"Length\\\",\\\"type\\\":\\\"UM\\\"},\\\"scaleOffset\\\":{\\\"offset\\\":0.0,\\\"scale\\\":0.3048},\\\"symbol\\\":\\\"ft\\\",\\\"type\\\":\\\"USO\\\"}\",\"type\": \"AnyCrsFeatureCollection\"}}}";
    private static final String GEO_JSON_CONVERTED_RECORD_2 = "{\"id\":\"geo-json-test-2\",\"kind\":\"geo-json:test:2.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0,\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\": {\"coordinates\": [313405.9477893702,6544797.620047403,6.56167],\"bbox\": null,\"type\": \"AnyCrsPoint\"},\"bbox\": null,\"properties\": {},\"type\": \"AnyCrsFeature\"}],\"bbox\": null,\"properties\":{},\"persistableReferenceCrs\": \"{\\\"lateBoundCRS\\\":\\\"wkt\\\":\\\"PROJCS[\\\\\\\"ED_1950_UTM_Zone_32N\\\\\\\",GEOGCS[\\\\\\\"GCS_European_1950\\\\\\\",DATUM[\\\\\\\"D_European_1950\\\\\\\",SPHEROID[\\\\\\\"International_1924\\\\\\\",6378388.0,297.0]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],PROJECTION[\\\\\\\"Transverse_Mercator\\\\\\\"],PARAMETER[\\\\\\\"False_Easting\\\\\\\",500000.0],PARAMETER[\\\\\\\"False_Northing\\\\\\\",0.0],PARAMETER[\\\\\\\"Central_Meridian\\\\\\\",9.0],PARAMETER[\\\\\\\"Scale_Factor\\\\\\\",0.9996],PARAMETER[\\\\\\\"Latitude_Of_Origin\\\\\\\",0.0],UNIT[\\\\\\\"Meter\\\\\\\",1.0],AUTHORITY[\\\\\\\"EPSG\\\\\\\",23032]]\\\",\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED_1950_UTM_Zone_32N\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"EPSG\\\",\\\"code\\\":\\\"23032\\\"},\\\"type\\\":\\\"LBC\\\"},\\\"singleCT\\\":{\\\"wkt\\\":\\\"GEOGTRAN[\\\\\\\"ED_1950_To_WGS_1984_23\\\\\\\",GEOGCS[\\\\\\\"GCS_European_1950\\\\\\\",DATUM[\\\\\\\"D_European_1950\\\\\\\",SPHEROID[\\\\\\\"International_1924\\\\\\\",6378388.0,297.0]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],GEOGCS[\\\\\\\"GCS_WGS_1984\\\\\\\",DATUM[\\\\\\\"D_WGS_1984\\\\\\\",SPHEROID[\\\\\\\"WGS_1984\\\\\\\",6378137.0,298.257223563]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433]],METHOD[\\\\\\\"Position_Vector\\\\\\\"],PARAMETER[\\\\\\\"X_Axis_Translation\\\\\\\",-116.641],PARAMETER[\\\\\\\"Y_Axis_Translation\\\\\\\",-56.931],PARAMETER[\\\\\\\"Z_Axis_Translation\\\\\\\",-110.559],PARAMETER[\\\\\\\"X_Axis_Rotation\\\\\\\",0.893],PARAMETER[\\\\\\\"Y_Axis_Rotation\\\\\\\",0.921],PARAMETER[\\\\\\\"Z_Axis_Rotation\\\\\\\",-0.917],PARAMETER[\\\\\\\"Scale_Difference\\\\\\\",-3.52],AUTHORITY[\\\\\\\"EPSG\\\\\\\",1612]]\\\",\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED_1950_To_WGS_1984_23\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"EPSG\\\",\\\"code\\\":\\\"1612\\\"},\\\"type\\\":\\\"ST\\\"},\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"ED50 * EPSG-Nor N62 2001 / UTM zone 32N [23032,1612]\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"SLB\\\",\\\"code\\\":\\\"23032023\\\"},\\\"type\\\":\\\"EBC\\\"}\",\"persistableReferenceUnitZ\": \"{\\\"baseMeasurement\\\":{\\\"ancestry\\\":\\\"Length\\\",\\\"type\\\":\\\"UM\\\"},\\\"scaleOffset\\\":{\\\"offset\\\":0.0,\\\"scale\\\":0.3048},\\\"symbol\\\":\\\"ft\\\",\\\"type\\\":\\\"USO\\\"}\",\"type\": \"AnyCrsFeatureCollection\"}, \"Wgs84Coordinates\": {\"type\": \"FeatureCollection\",\"bbox\": null,\"features\": [{\"type\": \"Feature\",\"bbox\": null,\"geometry\": {\"type\":\"Point\",\"bbox\": null,\"coordinates\": [5.7500000010406245,59.000000000399105,1.9999999999999998]},\"properties\": {}}],\"properties\": {},\"persistableReferenceCrs\": null,\"persistableReferenceUnitZ\": \"reference\"}}}}";

    @Test
    public void should_returnOriginalRecordsAndStatusesAsNoMetaBlock_whenProvidedRecordsWithoutMetaBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_2).getAsJsonObject());

        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);
        Assert.assertEquals(1, result.getConversionStatuses().size());
        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(RECORD_2));
        Assert.assertEquals("No Conversion Blocks exist in This Record.", result.getConversionStatuses().get(0).getErrors().get(0));
    }

    @Test
    public void should_returnRecordsAfterCrsConversion_whenProvidedRecordWithMetaBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_1).getAsJsonObject());
        this.originalRecords.add(this.jsonParser.parse(RECORD_3).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(CONVERTED_RECORD_1).getAsJsonObject());
        convertedRecords.add(this.jsonParser.parse(CONVERTED_RECORD_3).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus1 = new ConversionStatus();
        conversionStatus1.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus1.setId("unit-test-1");
        ConversionStatus conversionStatus2 = new ConversionStatus();
        conversionStatus2.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus2.setId("unit-test-3");
        conversionStatuses.add(conversionStatus1);
        conversionStatuses.add(conversionStatus2);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(2, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(CONVERTED_RECORD_1));
        Assert.assertTrue(result.getRecords().get(1).toString().equalsIgnoreCase(CONVERTED_RECORD_3));
    }

    @Test
    public void should_returnRecordsAfterCrsConversionTogetherWithNoMetaRecords_whenProvidedRMixedRecords() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_1).getAsJsonObject());
        this.originalRecords.add(this.jsonParser.parse(RECORD_2).getAsJsonObject());
        this.originalRecords.add(this.jsonParser.parse(RECORD_3).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(CONVERTED_RECORD_1).getAsJsonObject());
        convertedRecords.add(this.jsonParser.parse(CONVERTED_RECORD_3).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus1 = new ConversionStatus();
        conversionStatus1.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus1.setId("unit-test-1");
        ConversionStatus conversionStatus2 = new ConversionStatus();
        conversionStatus2.setStatus(ConvertStatus.NO_FRAME_OF_REFERENCE.toString());
        conversionStatus2.setId("unit-test-2");
        ConversionStatus conversionStatus3 = new ConversionStatus();
        conversionStatus3.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus3.setId("unit-test-3");
        conversionStatuses.add(conversionStatus1);
        conversionStatuses.add(conversionStatus2);
        conversionStatuses.add(conversionStatus3);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(3, result.getRecords().size());


        Assert.assertTrue(result.getRecords().contains(this.jsonParser.parse(CONVERTED_RECORD_1).getAsJsonObject()));
        Assert.assertTrue(result.getRecords().contains(this.jsonParser.parse(CONVERTED_RECORD_3).getAsJsonObject()));
        Assert.assertTrue(result.getRecords().contains(this.jsonParser.parse(RECORD_2).getAsJsonObject()));
    }


    @Test
    public void shouldConvertUnitsToSIWhenInputRecordHasValidMetaBlockAndData() {
        List<JsonObject> inputRecords = new ArrayList<>();
        String inputRecordString = "{\"id\": \"unit-test-10\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": 10.0},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"propertyNames\": [\"MD\"],\"name\": \"ft\"}]}";
        JsonObject inputRecord = (JsonObject) this.jsonParser.parse(inputRecordString);
        inputRecords.add(inputRecord);

        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        ConversionStatus conversionStatus1 = new ConversionStatus();
        conversionStatus1.setStatus(ConvertStatus.ERROR.toString());
        conversionStatus1.setId("unit-test-10");
        conversionStatus1.setErrors(errors);
        conversionStatuses.add(conversionStatus1);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(inputRecords);
        when(this.crsConversionService.doCrsConversion(any(), any())).thenReturn(crsConversionResult);

        RecordsAndStatuses result = this.sut.doConversion(inputRecords);

        List<ConversionStatus> resultStatuses = result.getConversionStatuses();
        Assert.assertTrue(resultStatuses.get(0).getErrors().size() == 0);

        List<JsonObject> resultRecords = result.getRecords();
        Assert.assertEquals(1, resultRecords.size());
        JsonObject resultRecord = resultRecords.get(0);
        JsonElement data = resultRecord.get("data");
        double  actualMDValue = data.getAsJsonObject().get("MD").getAsDouble();
        Assert.assertEquals(3.048, actualMDValue, 0.00001);
    }


    @Test
    public void shouldConvertUnitsToSIAndCrsToWgs84WhenInputRecordsHaveValidMetaBlockAndData() {
        List<JsonObject> inputRecords = new ArrayList<>();
        inputRecords.add(this.jsonParser.parse(RECORD_1).getAsJsonObject());
        String inputRecordString = "{\"id\": \"unit-test-10\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": 10.0},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"propertyNames\": [\"MD\"],\"name\": \"ft\"}]}";
        JsonObject inputRecord = (JsonObject) this.jsonParser.parse(inputRecordString);
        inputRecords.add(inputRecord);

        List<JsonObject> crsConvertedRecords = new ArrayList<>();
        crsConvertedRecords.add(this.jsonParser.parse(CONVERTED_RECORD_1).getAsJsonObject());
        crsConvertedRecords.add(inputRecord);

        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        ConversionStatus conversionStatus1 = new ConversionStatus();
        conversionStatus1.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus1.setId("unit-test-1");
        conversionStatus1.setErrors(errors);
        ConversionStatus conversionStatus2 = new ConversionStatus();
        conversionStatus2.setStatus(ConvertStatus.ERROR.toString());
        conversionStatus2.setId("unit-test-10");
        conversionStatus2.setErrors(errors);
        conversionStatuses.add(conversionStatus1);
        conversionStatuses.add(conversionStatus2);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(crsConvertedRecords);

        when(this.crsConversionService.doCrsConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(inputRecords);
        List<ConversionStatus> resultStatuses = result.getConversionStatuses();
        List<JsonObject> resultRecords = result.getRecords();
        Assert.assertEquals(2, resultRecords.size());
        Assert.assertTrue(resultRecords.get(0).toString().equalsIgnoreCase(CONVERTED_RECORD_1));
        Assert.assertTrue(resultStatuses.get(1).getErrors().size() == 0);
        JsonObject resultRecord = resultRecords.get(1);
        JsonElement data = resultRecord.get("data");
        double  actualMDValue = data.getAsJsonObject().get("MD").getAsDouble();
        Assert.assertEquals(3.048, actualMDValue, 0.00001);
    }

    @Test
    public void should_returnRecordsAfterCrsConversionTogetherWithNoMetaRecordsAndLogMissing_whenProvidedMixedRecords_OneRecordMissing() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_1).getAsJsonObject());
        this.originalRecords.add(this.jsonParser.parse(RECORD_2).getAsJsonObject());
        this.originalRecords.add(this.jsonParser.parse(RECORD_3).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(CONVERTED_RECORD_1).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus1 = new ConversionStatus();
        conversionStatus1.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus1.setId("unit-test-1");
        ConversionStatus conversionStatus2 = new ConversionStatus();
        conversionStatus2.setStatus(ConvertStatus.NO_FRAME_OF_REFERENCE.toString());
        conversionStatus2.setId("unit-test-2");
        ConversionStatus conversionStatus3 = new ConversionStatus();
        conversionStatus3.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus3.setId("unit-test-3");
        conversionStatuses.add(conversionStatus1);
        conversionStatuses.add(conversionStatus2);
        conversionStatuses.add(conversionStatus3);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(2, result.getRecords().size());

        verify(this.logger).warning("Missing record after conversion: unit-test-3");
        Assert.assertTrue(result.getRecords().contains(this.jsonParser.parse(CONVERTED_RECORD_1).getAsJsonObject()));
        Assert.assertTrue(result.getRecords().contains(this.jsonParser.parse(RECORD_2).getAsJsonObject()));
    }

    @Test
    public void should_returnOriginalRecordsAndStatusesAsNoMetaAndAsIngestedCoordinatesBlocks_whenProvidedRecordsWithoutConversionBlocks() {
        this.originalRecords.add(this.jsonParser.parse(GEO_JSON_RECORD_1).getAsJsonObject());

        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);
        Assert.assertEquals(1, result.getConversionStatuses().size());
        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(GEO_JSON_RECORD_1));
        Assert.assertEquals("No Conversion Blocks exist in This Record.", result.getConversionStatuses().get(0).getErrors().get(0));
    }

    @Test
    public void should_returnOriginalRecordsAndStatusesAsIngestedCoordinatesBlockWithWgs84Coordinates_whenProvidedRecordsWithWgs84Coordinates() {
        this.originalRecords.add(this.jsonParser.parse(GEO_JSON_CONVERTED_RECORD_2).getAsJsonObject());

        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);
        Assert.assertEquals(1, result.getConversionStatuses().size());
        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertEquals("'Wgs84Coordinates' block exists, Conversion is not required for this record.", result.getConversionStatuses().get(0).getErrors().get(0));
    }
}