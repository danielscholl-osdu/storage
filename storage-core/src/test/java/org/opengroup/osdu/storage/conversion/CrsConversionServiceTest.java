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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.core.common.crs.CrsConversionServiceErrorMessages;
import org.opengroup.osdu.core.common.model.crs.*;
import org.opengroup.osdu.core.common.model.storage.ConversionStatus;
import org.opengroup.osdu.core.common.model.crs.CrsPropertySet;
import org.opengroup.osdu.core.common.model.crs.RecordsAndStatuses;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.crs.CrsConverterFactory;
import org.opengroup.osdu.core.common.crs.CrsConverterService;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CrsConversionServiceTest {

    @Mock
    private CrsConverterFactory crsConverterFactory;

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private CrsConverterService crsConverterService;

    @Mock
    private CrsPropertySet crsPropertySet;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private IServiceAccountJwtClient jwtClient;

    @InjectMocks
    private CrsConversionService sut;

    private List<JsonObject> originalRecords = new ArrayList<>();
    private List<ConversionStatus.ConversionStatusBuilder> conversionStatuses = new ArrayList<>();
    private List<Point> convertedPoints = new ArrayList<>();
    private ConvertPointsResponse convertPointsResponse = new ConvertPointsResponse();
    private JsonParser jsonParser = new JsonParser();
    private Set<String> nestedPropertyNames = new HashSet<>();
    private Map<String, String> pairProperty = new HashMap<>();

    private static final String RECORD_1 = "{\"id\":\"unit-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_2 = "{\"id\":\"unit-test-2\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_3 = "{\"id\":\"unit-test-3\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"unit\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_4 = "{\"id\":\"unit-test-4\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_5 = "{\"id\":\"unit-test-5\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_6 = "{\"id\":\"unit-test-6\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\",\"T\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_7 = "{\"id\":\"unit-test-7\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_8 = "{\"id\":\"unit-test-8\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"LON\":16.00,\"LAT\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"LON\",\"LAT\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_9 = "{\"id\":\"unit-test-9\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"nested\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"nestedProperty\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_10 = "{\"id\":\"unit-test-10\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\"},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"validNestedProperty\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_11 = "{\"id\":\"unit-test-11\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"validNestedProperty\":{\"crsKey\":\"Native\"}},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"validNestedProperty\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_12 = "{\"id\":\"unit-test-12\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"validNestedProperty\":{\"crsKey\":\"Native\",\"points\":[[16.00,10.00],[16.00,10.00]]}},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"validNestedProperty\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_13 = "{\"id\":\"unit-test-13\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.45,\"Y\":10.07,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_14 = "{\"id\":\"unit-test-14\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":null,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_15 = "{\"id\":\"unit-test-15\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":\"yes\",\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_16 = "{\"id\":\"unit-test-16\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_17 = "{\"id\":\"unit-test-17\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"validNestedProperty\":[[16.00,10.00],[16.00,10.00]]},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"validNestedProperty\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_18 = "{\"id\":\"unit-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"x\":16.00,\"y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"x\",\"y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_19 = "{\"id\":\"unit-test-3\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":{\"path\":\"\",\"kind\":\"unit\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}}";
    private static final String RECORD_20 = "{\"id\":\"unit-test-20\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"Nested\":{\"X\":10.0,\"Y\":10.00}},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"Nested.X\",\"Nested.Y\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_21 = "{\"id\":\"unit-test-21\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"Nested\":{\"X\":10.0,\"Y\":10.00}},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\", \"persistableReference\": { \"scaleOffset\": {\"scale\": 0.3048, \"offset\": 0 }, \"symbol\": \"ft/s\", \"baseMeasurement\": { \"type\": \"UM\", \"ancestry\": \"Velocity\" }, \"type\": \"USO\" } ,\"propertyNames\":[\"Nested.X\",\"Nested.Y\"],\"name\":\"GCS_WGS_1984\"}]}";


    private static final String CONVERTED_RECORD_1 = "{\"id\":\"unit-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":15788.036,\"Y\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_2 = "{\"id\":\"unit-test-2\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":15788.036,\"Y\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_3 = "{\"id\":\"unit-test-7\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":15788.036,\"Y\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"X\",\"Y\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_4 = "{\"id\":\"unit-test-8\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"LON\":15788.036,\"LAT\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"LON\",\"LAT\",\"Z\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_5 = "{\"id\":\"unit-test-12\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"validNestedProperty\":{\"crsKey\":\"Native\",\"points\":[[15788.036,9567.4,0.0],[15788.036,9567.4,0.0]]}},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"validNestedProperty\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String TO_CRS = "{\\\"wkt\\\":\\\"GEOGCS[\\\\\\\"GCS_WGS_1984\\\\\\\",DATUM[\\\\\\\"D_WGS_1984\\\\\\\",SPHEROID[\\\\\\\"WGS_1984\\\\\\\",6378137.0,298.257223563]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433],AUTHORITY[\\\\\\\"EPSG\\\\\\\",4326]]\\\",\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"GCS_WGS_1984\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"EPSG\\\",\\\"code\\\":\\\"4326\\\"},\\\"type\\\":\\\"LBC\\\"}";
    private static final String CONVERTED_RECORD_6 = "{\"id\":\"unit-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"x\":15788.036,\"y\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_7 = "{\"id\":\"unit-test-6\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":15788.036,\"Y\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"X\",\"Y\",\"Z\",\"T\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_8 = "{\"id\":\"unit-test-20\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"Nested\":{\"X\":15788.036,\"Y\":9567.4},\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"Nested.X\",\"Nested.Y\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_9 = "{\"id\":\"unit-test-21\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"Nested\":{\"X\":15788.036,\"Y\":9567.4},\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"Nested.X\",\"Nested.Y\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";

    @Before
    public void setup() throws Exception{
        Point convertedPoint1 = new Point();
        convertedPoint1.setZ(0.0);
        convertedPoint1.setY(9567.40);
        convertedPoint1.setX(15788.036);
        Point convertedPoint2 = new Point();
        convertedPoint2.setZ(0.0);
        convertedPoint2.setY(9567.40);
        convertedPoint2.setX(15788.036);
        this.convertedPoints.add(convertedPoint1);
        this.convertedPoints.add(convertedPoint2);
        this.convertPointsResponse.setPoints(this.convertedPoints);

        this.nestedPropertyNames.add("validNestedProperty");
        this.pairProperty.put("x", "y");
        this.pairProperty.put("lon", "lat");

        when(this.crsPropertySet.getPropertyPairing()).thenReturn(this.pairProperty);
        when(this.crsPropertySet.getNestedPropertyNames()).thenReturn(this.nestedPropertyNames);

        when(this.crsConverterFactory.create(any())).thenReturn(this.crsConverterService);
        when(this.crsConverterService.convertPoints(any())).thenReturn(this.convertPointsResponse);

        when(this.jwtClient.getIdToken(any())).thenReturn("auth-token-unit-test");
    }

    @Test
    public void should_returnOriginalRecordAndBadValueStatus_WhenBadRequestFromCrsConverter() throws Exception{
        this.originalRecords.add(this.jsonParser.parse(RECORD_13).getAsJsonObject());
        HttpResponse response = new HttpResponse();
        response.setResponseCode(HttpStatus.SC_BAD_REQUEST);
        CrsConverterException exception = new CrsConverterException("bad persistable reference", response);
        when(this.crsConverterService.convertPoints(any())).thenThrow(exception);
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-13").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        Assert.assertEquals(2, crsResult.getConversionStatuses().get(0).getErrors().size());
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_13));
    }

    @Test
    public void should_returnOriginalRecordAndEmptyStatus_whenMetaIsNotCrsType() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_3).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-3").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_3));
    }

    @Test
    public void should_returnOriginalRecordAndErrorMessage_whenErrorParsingMetaBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_19).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-3").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_19));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMetaIsProvidedButDataMissingInDataBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_4).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-4").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_4));
        String message = String.format(CrsConversionServiceErrorMessages.MISSING_PROPERTY,"X");
        List<String> errorMsg = crsResult.getConversionStatuses().get(0).getErrors();
        Assert.assertEquals(2, errorMsg.size());
        Assert.assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'z'."));
        Assert.assertTrue(errorMsg.contains(message));
    }

    @Test
    public void should_returnConvertedRecordAndConversionStatus_whenMetaIsProvidedWithMultiplePairOfCoordinates() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_6).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-6").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());

        String converted = String.format(CONVERTED_RECORD_7, TO_CRS);
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
        List<String> errorMsg = crsResult.getConversionStatuses().get(0).getErrors();
        Assert.assertEquals(2, errorMsg.size());
        Assert.assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'z'."));
        Assert.assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 't'."));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMetaIsProvidedButMissingMandatoryPropertiesXOrY() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_5).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-5").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_5));
        List<String> errorMsg = crsResult.getConversionStatuses().get(0).getErrors();
        Assert.assertEquals(2, errorMsg.size());
        Assert.assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'y'."));
        Assert.assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'z'."));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenZIsNotProvided() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_7).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-7").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_3, TO_CRS);
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenValidRecordsProvided() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_1).getAsJsonObject());
        this.originalRecords.add(this.jsonParser.parse(RECORD_2).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-1").status(ConvertStatus.SUCCESS.toString()));
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-2").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(2, crsResult.getRecords().size());
        Assert.assertEquals(2, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_1, TO_CRS);
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
        String converted1 = String.format(CONVERTED_RECORD_2, TO_CRS);
        Assert.assertTrue(crsResult.getRecords().get(1).toString().equalsIgnoreCase(converted1));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenValidRecordsProvided_PropertyLowerCase() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_18).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-1").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_6, TO_CRS);
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenRecordsHasXYFieldWithOtherNames() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_8).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-8").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_4, TO_CRS);
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenInapproriateNestedPropertyNameProvided() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_9).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-9").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        String message = String.format(CrsConversionServiceErrorMessages.INVALID_NESTED_PROPERTY_NAME,"nestedProperty");
        Assert.assertTrue(crsResult.getConversionStatuses().get(0).getErrors().get(0).equalsIgnoreCase(message));
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_9));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMissingNestedPropertyInDataBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_10).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-10").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        String message = String.format(CrsConversionServiceErrorMessages.MISSING_PROPERTY,"validNestedProperty");
        Assert.assertTrue(crsResult.getConversionStatuses().get(0).getErrors().get(0).equalsIgnoreCase(message));
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_10));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMetaIsProvidedButDataIsNullInDataBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_14).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-14").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_14));
        String message = String.format(CrsConversionServiceErrorMessages.MISSING_PROPERTY,"X");
        List<String> errorMsg = crsResult.getConversionStatuses().get(0).getErrors();
        Assert.assertEquals(2, errorMsg.size());
        Assert.assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'z'."));
        Assert.assertTrue(errorMsg.contains(message));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMetaIsProvidedButDataIsIllegalInDataBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_15).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-15").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_15));
        String message = String.format(CrsConversionServiceErrorMessages.ILLEGAL_PROPERTY_VALUE,"X", "For input string: \"yes\"");
        List<String> errorMsg = crsResult.getConversionStatuses().get(0).getErrors();
        Assert.assertEquals(2, errorMsg.size());
        Assert.assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'z'."));
        Assert.assertTrue(errorMsg.contains(message));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMetaIsProvidedButNoDataBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_16).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-16").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_16));
        Assert.assertTrue(crsResult.getConversionStatuses().get(0).getErrors().get(0).equalsIgnoreCase(CrsConversionServiceErrorMessages.MISSING_DATA_BLOCK));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMissingPointsListINNestedProperty() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_11).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-11").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        Assert.assertTrue(crsResult.getConversionStatuses().get(0).getErrors().get(0).equalsIgnoreCase(CrsConversionServiceErrorMessages.MISSING_POINTS_IN_NESTED_PROPERTY));
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_11));
    }

    @Test
    public void should_returnConvertedRecordAndConversionStatus_whenNestedPropertyProvided() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_12).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-12").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_5, TO_CRS);
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenNestedPropertyNotProvidedAsJsonObject() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_17).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-17").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        String message = String.format(CrsConversionServiceErrorMessages.ILLEGAL_DATA_IN_NESTED_PROPERTY, "validNestedProperty","Not a JSON Object: [[16.00,10.00],[16.00,10.00]]");
        Assert.assertTrue(crsResult.getConversionStatuses().get(0).getErrors().get(0).equalsIgnoreCase(message));
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_17));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenNestedDataIsNotProvided() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_20).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-20").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_8, TO_CRS);
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenPersistableReferenceIsJsonObject() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_21).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-21").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        Assert.assertEquals(1, crsResult.getRecords().size());
        Assert.assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_9, TO_CRS);
        Assert.assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }
}

