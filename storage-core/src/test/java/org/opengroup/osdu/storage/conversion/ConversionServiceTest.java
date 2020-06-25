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


    @Test
    public void should_returnOriginalRecordsAndStatusesAsNoMetaBlock_whenProvidedRecordsWithoutMetaBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_2).getAsJsonObject());


        RecordsAndStatuses result = this.sut.doConversion(this.originalRecords);
        Assert.assertEquals(1, result.getConversionStatuses().size());
        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertTrue(result.getRecords().get(0).toString().equalsIgnoreCase(RECORD_2));
        Assert.assertEquals("No Meta Block in This Record.", result.getConversionStatuses().get(0).getErrors().get(0));
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

}

