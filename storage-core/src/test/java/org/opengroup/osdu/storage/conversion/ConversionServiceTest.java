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
import org.opengroup.osdu.core.common.crs.CrsConversionServiceErrorMessages;
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

    private static final String GEO_JSON_RECORD = "{\"id\":\"geo-json-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"geo-json-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0,\"SpatialLocation\":{\"AsIngestedCoordinates1\":{}}}}";
    private static final String GEO_JSON_RECORD_1 = "{\"id\":\"geo-json-point-test\",\"kind\":\"geo-json-point:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[313405.9477893702,6544797.620047403,6.561679790026246],\"bbox\":null,\"type\":\"AnyCrsPoint\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"CrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_POINT_RECORD = "{\"id\":\"geo-json-point-test\",\"kind\":\"geo-json-point:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[313405.9477893702,6544797.620047403,6.561679790026246],\"bbox\":null,\"type\":\"AnyCrsPoint\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_POINT_CONVERTED_RECORD = "{\"id\":\"geo-json-point-test\",\"kind\":\"geo-json-point:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[313405.9477893702,6544797.620047403,6.561679790026246],\"bbox\":null,\"type\":\"AnyCrsPoint\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"Wgs84Coordinates\":{\"type\":\"FeatureCollection\",\"bbox\":null,\"features\":[{\"type\":\"Feature\",\"bbox\":null,\"geometry\":{\"type\":\"Point\",\"bbox\":null,\"coordinates\":[5.7500000010406245,59.000000000399105,1.9999999999999998]},\"properties\":{}}],\"properties\":{},\"persistableReferenceCrs\":null,\"persistableReferenceUnitZ\":\"reference\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_MULTIPOINT_RECORD = "{\"id\":\"geo-json-multi-point-test\",\"kind\":\"geo-json-multi-point:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[313405.9477893702,6544797.620047403,6.561679790026246],\"bbox\":null,\"type\":\"AnyCrsPoint\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_MULTIPOINT_CONVERTED_RECORD = "{\"id\":\"geo-json-multi-point-test\",\"kind\":\"geo-json-multi-point:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[313405.9477893702,6544797.620047403,6.561679790026246],\"bbox\":null,\"type\":\"AnyCrsPoint\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"Wgs84Coordinates\":{\"type\":\"FeatureCollection\",\"bbox\":null,\"features\":[{\"type\":\"Feature\",\"bbox\":null,\"geometry\":{\"type\":\"MultiPoint\",\"bbox\":null,\"coordinates\":[[5.7500000010406245,59.000000000399105,1.9999999999999998]]},\"properties\":{}}],\"properties\":{},\"persistableReferenceCrs\":null,\"persistableReferenceUnitZ\":\"reference\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_POLYGON_RECORD = "{\"id\":\"geo-json-polygon-test\",\"kind\":\"geo-json-polygon:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[[[438727.0,6475514.4],[431401.3,6477341.0],[432562.5,6481998.4],[439888.3,6480171.9],[438727.0,6475514.4]]],\"bbox\":null,\"type\":\"AnyCrsPolygon\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_POLYGON_CONVERTED_RECORD = "{\"id\":\"geo-json-polygon-test\",\"kind\":\"geo-json-polygon:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[[[438727.0,6475514.4],[431401.3,6477341.0],[432562.5,6481998.4],[439888.3,6480171.9],[438727.0,6475514.4]]],\"bbox\":null,\"type\":\"AnyCrsPolygon\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"Wgs84Coordinates\":{\"type\":\"FeatureCollection\",\"bbox\":null,\"features\":[{\"type\":\"Feature\",\"bbox\":null,\"geometry\":{\"type\":\"Polygon\",\"bbox\":null,\"coordinates\":[[[7.949867128288194,58.4142370248961],[7.823960300628539,58.429550602683086],[7.842466972403941,58.47155277852263],[7.968517917956573,58.456222421251454],[7.949867128288194,58.4142370248961]]]},\"properties\":{}}],\"properties\":{},\"persistableReferenceCrs\":null,\"persistableReferenceUnitZ\":\"reference\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_MULTI_POLYGON_RECORD = "{\"id\":\"geo-json-multi-polygon-test\",\"kind\":\"geo-json-multi-polygon:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[[[[30,20],[45,40],[10,40],[30,20]]],[[[15,5],[40,10],[10,20],[5,10],[15,5]]]],\"bbox\":null,\"type\":\"AnyCrsMultiPolygon\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_MULTI_POLYGON_CONVERTED_RECORD = "{\"id\":\"geo-json-multi-polygon-test\",\"kind\":\"geo-json-multi-polygon:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[[[[30,20],[45,40],[10,40],[30,20]]],[[[15,5],[40,10],[10,20],[5,10],[15,5]]]],\"bbox\":null,\"type\":\"AnyCrsMultiPolygon\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"Wgs84Coordinates\":{\"type\":\"FeatureCollection\",\"bbox\":null,\"features\":[{\"type\":\"Feature\",\"bbox\":null,\"geometry\":{\"type\":\"MultiPolygon\",\"bbox\":null,\"coordinates\":[[[[4.511019149215454,-0.001056580595758948],[4.51115353060209,-8.761959905224556E-4],[4.51083997196578,-8.761976198248965E-4],[4.511019149215454,-0.001056580595758948]]],[[[4.5108847675989985,-0.0011918691657301806],[4.511108737840292,-0.0011467721171607886],[4.510839972868457,-0.0010565814822037596],[4.510795179223357,-0.0011467736294272298],[4.5108847675989985,-0.0011918691657301806]]]]},\"properties\":{}}],\"properties\":{},\"persistableReferenceCrs\":null,\"persistableReferenceUnitZ\":\"reference\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_LINE_STRING_RECORD = "{\"id\":\"geo-json-line-string-test\",\"kind\":\"geo-json-line-string:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[[501000.0,7001000.0],[502000.0,7002000.0]],\"bbox\":null,\"type\":\"AnyCrsLineString\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_LINE_STRING_CONVERTED_RECORD = "{\"id\":\"geo-json-line-string-test\",\"kind\":\"geo-json-line-string:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[[501000.0,7001000.0],[502000.0,7002000.0]],\"bbox\":null,\"type\":\"AnyCrsLineString\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"Wgs84Coordinates\":{\"type\":\"FeatureCollection\",\"bbox\":null,\"features\":[{\"type\":\"Feature\",\"bbox\":null,\"geometry\":{\"type\":\"LineString\",\"bbox\":null,\"coordinates\":[[9.018268130018686,63.136450807807265],[9.0381148358597,63.145421923557194]]},\"properties\":{}}],\"properties\":{},\"persistableReferenceCrs\":null,\"persistableReferenceUnitZ\":\"reference\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_MULTI_LINE_STRING_RECORD = "{\"id\":\"geo-json-multi-line-string-test\",\"kind\":\"geo-json-multi-line-string:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[[[501000.0,7001000.0],[502000.0,7002000.0]]],\"bbox\":null,\"type\":\"AnyCrsMultiLineString\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_MULTI_LINE_STRING_CONVERTED_RECORD = "{\"id\":\"geo-json-multi-line-string-test\",\"kind\":\"geo-json-multi-line-string:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[[[501000.0,7001000.0],[502000.0,7002000.0]]],\"bbox\":null,\"type\":\"AnyCrsMultiLineString\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"Wgs84Coordinates\":{\"type\":\"FeatureCollection\",\"bbox\":null,\"features\":[{\"type\":\"Feature\",\"bbox\":null,\"geometry\":{\"type\":\"MultiLineString\",\"bbox\":null,\"coordinates\":[[[9.018268130018686,63.136450807807265],[9.0381148358597,63.145421923557194]]]},\"properties\":{}}],\"properties\":{},\"persistableReferenceCrs\":null,\"persistableReferenceUnitZ\":\"reference\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_GEOMETRY_COLLECTION_RECORD = "{\"id\":\"geo-json-geometry-collection-test\",\"kind\":\"geo-json-geometry-collection:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"type\":\"AnyCrsGeometryCollection\",\"bbox\":null,\"geometries\":[{\"type\":\"Point\",\"bbox\":null,\"coordinates\":[500000.0,7000000.0]},{\"type\":\"LineString\",\"bbox\":null,\"coordinates\":[[501000.0,7001000.0],[502000.0,7002000.0]]}]},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";
    private static final String ANY_CRS_GEOMETRY_COLLECTION_CONVERTED_RECORD = "{\"id\":\"geo-json-geometry-collection-test\",\"kind\":\"geo-json-geometry-collection:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"type\":\"AnyCrsGeometryCollection\",\"bbox\":null,\"geometries\":[{\"type\":\"Point\",\"bbox\":null,\"coordinates\":[500000.0,7000000.0]},{\"type\":\"LineString\",\"bbox\":null,\"coordinates\":[[501000.0,7001000.0],[502000.0,7002000.0]]}]},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"Wgs84Coordinates\":{\"type\":\"FeatureCollection\",\"bbox\":null,\"features\":[{\"type\":\"Feature\",\"bbox\":null,\"geometry\":{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\",\"coordinates\":[8.998433675254244,63.1274769068748]},{\"type\":\"LineString\",\"coordinates\":[[9.018268130018686,63.136450807807265],[9.0381148358597,63.145421923557194]]}]},\"properties\":{}}],\"properties\":{},\"persistableReferenceCrs\":null,\"persistableReferenceUnitZ\":\"reference\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";

    @Test
    public void should_returnOriginalRecordsAndStatusesAsNoMetaBlock_whenProvidedRecordsWithoutMetaBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_2).getAsJsonObject());

        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);
        Assert.assertEquals(1, result.getConversionStatuses().size());
        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(RECORD_2));
        Assert.assertEquals(CrsConversionServiceErrorMessages.MISSING_META_BLOCK, result.getConversionStatuses().get(0).getErrors().get(0));
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
        this.originalRecords.add(this.jsonParser.parse(GEO_JSON_RECORD).getAsJsonObject());

        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);
        Assert.assertEquals(1, result.getConversionStatuses().size());
        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(GEO_JSON_RECORD));
        Assert.assertEquals(CrsConversionServiceErrorMessages.MISSING_AS_INGESTED_COORDINATES, result.getConversionStatuses().get(0).getErrors().get(0));
    }

    @Test
    public void should_returnOriginalRecordsAndStatusesAsNoMetaAndAsIngestedCoordinatesBlocks_whenProvidedRecordsWithInvalidType() {
        this.originalRecords.add(this.jsonParser.parse(GEO_JSON_RECORD_1).getAsJsonObject());
        String type = "CrsFeatureCollection";

        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);
        Assert.assertEquals(1, result.getConversionStatuses().size());
        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(GEO_JSON_RECORD_1));
        Assert.assertEquals(String.format(CrsConversionServiceErrorMessages.MISSING_AS_INGESTED_TYPE, type), result.getConversionStatuses().get(0).getErrors().get(0));
    }

    @Test
    public void should_returnRecordsAfterCrsConversion_whenProvidedRecordWithAsIngestedCoordinatesBlockTypePoint() {
        this.originalRecords.add(this.jsonParser.parse(ANY_CRS_POINT_RECORD).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(ANY_CRS_POINT_CONVERTED_RECORD).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus = new ConversionStatus();
        conversionStatus.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus.setId("geo-json-point-test");
        conversionStatuses.add(conversionStatus);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsGeoJsonConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(ANY_CRS_POINT_CONVERTED_RECORD));
    }

    @Test
    public void should_returnRecordsAfterCrsConversion_whenProvidedRecordWithAsIngestedCoordinatesBlockTypeMultiPoint() {
        this.originalRecords.add(this.jsonParser.parse(ANY_CRS_MULTIPOINT_RECORD).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(ANY_CRS_MULTIPOINT_CONVERTED_RECORD).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus = new ConversionStatus();
        conversionStatus.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus.setId("geo-json-multi-point-test");
        conversionStatuses.add(conversionStatus);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsGeoJsonConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(ANY_CRS_MULTIPOINT_CONVERTED_RECORD));
    }

    @Test
    public void should_returnRecordsAfterCrsConversion_whenProvidedRecordWithAsIngestedCoordinatesBlockTypePolygon() {
        this.originalRecords.add(this.jsonParser.parse(ANY_CRS_POLYGON_RECORD).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(ANY_CRS_POLYGON_CONVERTED_RECORD).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus = new ConversionStatus();
        conversionStatus.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus.setId("geo-json-polygon-test");
        conversionStatuses.add(conversionStatus);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsGeoJsonConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(ANY_CRS_POLYGON_CONVERTED_RECORD));
    }

    @Test
    public void should_returnRecordsAfterCrsConversion_whenProvidedRecordWithAsIngestedCoordinatesBlockTypeMultiPolygon() {
        this.originalRecords.add(this.jsonParser.parse(ANY_CRS_MULTI_POLYGON_RECORD).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(ANY_CRS_MULTI_POLYGON_CONVERTED_RECORD).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus = new ConversionStatus();
        conversionStatus.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus.setId("geo-json-multi-polygon-test");
        conversionStatuses.add(conversionStatus);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsGeoJsonConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(ANY_CRS_MULTI_POLYGON_CONVERTED_RECORD));
    }

    @Test
    public void should_returnRecordsAfterCrsConversion_whenProvidedRecordWithAsIngestedCoordinatesBlockTypeLineString() {
        this.originalRecords.add(this.jsonParser.parse(ANY_CRS_LINE_STRING_RECORD).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(ANY_CRS_LINE_STRING_CONVERTED_RECORD).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus = new ConversionStatus();
        conversionStatus.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus.setId("geo-json-line-string-test");
        conversionStatuses.add(conversionStatus);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsGeoJsonConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(ANY_CRS_LINE_STRING_CONVERTED_RECORD));
    }

    @Test
    public void should_returnRecordsAfterCrsConversion_whenProvidedRecordWithAsIngestedCoordinatesBlockTypeMultiLineString() {
        this.originalRecords.add(this.jsonParser.parse(ANY_CRS_MULTI_LINE_STRING_RECORD).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(ANY_CRS_MULTI_LINE_STRING_CONVERTED_RECORD).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus = new ConversionStatus();
        conversionStatus.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus.setId("geo-json-multi-line-string-test");
        conversionStatuses.add(conversionStatus);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsGeoJsonConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(ANY_CRS_MULTI_LINE_STRING_CONVERTED_RECORD));
    }

    @Test
    public void should_returnRecordsAfterCrsConversion_whenProvidedRecordWithAsIngestedCoordinatesBlockTypeGeometryCollection() {
        this.originalRecords.add(this.jsonParser.parse(ANY_CRS_GEOMETRY_COLLECTION_RECORD).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(ANY_CRS_GEOMETRY_COLLECTION_CONVERTED_RECORD).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus = new ConversionStatus();
        conversionStatus.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus.setId("geo-json-geometry-collection-test");
        conversionStatuses.add(conversionStatus);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsGeoJsonConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(ANY_CRS_GEOMETRY_COLLECTION_CONVERTED_RECORD));
    }

    @Test
    public void should_returnRecordsAfterCrsConversion_whenProvidedRecordWithAsIngestedCoordinatesWithMultipleTypes() {
        this.originalRecords.add(this.jsonParser.parse(ANY_CRS_POINT_RECORD).getAsJsonObject());
        this.originalRecords.add(this.jsonParser.parse(ANY_CRS_POLYGON_RECORD).getAsJsonObject());

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(this.jsonParser.parse(ANY_CRS_POINT_CONVERTED_RECORD).getAsJsonObject());
        convertedRecords.add(this.jsonParser.parse(ANY_CRS_POLYGON_CONVERTED_RECORD).getAsJsonObject());
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus1 = new ConversionStatus();
        conversionStatus1.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus1.setId("geo-json-point-test");
        ConversionStatus conversionStatus2 = new ConversionStatus();
        conversionStatus2.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus2.setId("geo-json-polygon-test");
        conversionStatuses.add(conversionStatus1);
        conversionStatuses.add(conversionStatus2);
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(convertedRecords);

        when(this.crsConversionService.doCrsGeoJsonConversion(any(), any())).thenReturn(crsConversionResult);
        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);

        Assert.assertEquals(2, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(ANY_CRS_POINT_CONVERTED_RECORD));
        Assert.assertTrue(result.getRecords().get(1).toString().equalsIgnoreCase(ANY_CRS_POLYGON_CONVERTED_RECORD));
    }
}