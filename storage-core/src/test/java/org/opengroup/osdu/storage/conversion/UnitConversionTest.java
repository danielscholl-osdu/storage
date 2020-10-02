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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import org.opengroup.osdu.core.common.crs.UnitConversionImpl;
import org.opengroup.osdu.core.common.model.crs.ConversionRecord;
import org.opengroup.osdu.core.common.model.crs.ConvertStatus;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class UnitConversionTest {

    private JsonParser jsonParser = new JsonParser();
    private UnitConversionImpl unitConversion = new UnitConversionImpl();

    @Before
    public void setup() throws Exception{
        this.unitConversion = new UnitConversionImpl();
    }

    @Test
    public void shouldReturnOriginalRecordWhenMetaIsMissing() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"acl\": {\"viewers\": [\"viewers@unittest.com\"],\"owners\": [\"owners@unittest.com\"]},\"legal\": {\"legaltags\": [\"unit-test-legal\"],\"otherRelevantDataCountries\": [\"AA\"]},\"data\": {\"msg\": \"testing record 1\",\"X\": 16.00,\"Y\": 10.00,\"Z\": 0}}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConvertStatus() == ConvertStatus.NO_FRAME_OF_REFERENCE);
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().size() == 0);
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
    }

    @Test
    public void shouldReturnOriginalRecordWhenUnitIsMissingInMeta() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"acl\": {\"viewers\": [\"viewers@unittest.com\"],\"owners\": [\"owners@unittest.com\"]},\"legal\": {\"legaltags\": [\"unit-test-legal\"],\"otherRelevantDataCountries\": [\"AA\"]},\"data\": {\"msg\": \"testing record 1\",\"X\": 16.00,\"Y\": 10.00,\"Z\": 0},\"meta\": [{\"path\": \"\",\"kind\": \"CRS\",\"persistableReference\": \"reference\",\"propertyNames\": [\"X\",\"Y\",\"Z\"],\"name\": \"name\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().size() == 0);
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
    }

    @Test
    public void shouldReturnOriginalRecordWhenReferenceIsMissingInMeta() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": 10.0},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"propertyNames\": [\"MD\"],\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConvertStatus() == ConvertStatus.ERROR);
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().get(0).equalsIgnoreCase(UnitConversionImpl.MISSING_REFERENCE));
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
    }

    @Test
    public void shouldReturnOriginalRecordWhenReferenceIsInvalidInMeta() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": 10.0},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"reference\",\"propertyNames\": [\"MD\"],\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConvertStatus() == ConvertStatus.ERROR);
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().get(0).equalsIgnoreCase(UnitConversionImpl.INVALID_REFERENCE));
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
    }

    @Test
    public void shouldReturnOriginalRecordWhenPropertyNamesAreMissingInMeta() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": 10.0},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConvertStatus() == ConvertStatus.ERROR);
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().get(0).equalsIgnoreCase(UnitConversionImpl.MISSING_PROPERTY_NAMES));
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
    }

    @Test
    public void shouldReturnOriginalRecordWhenMetaDataKindIsMissingInMeta() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": 10.0},\"meta\": [{\"path\": \"\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"propertyNames\": [\"MD\"],\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConvertStatus() == ConvertStatus.ERROR);
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().get(0).equalsIgnoreCase(UnitConversionImpl.MISSING_META_KIND));
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
    }

    @Test
    public void shouldReturnOriginalRecordWhenPropertyNamesAreNotArrayInMeta() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": 10.0},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"propertyNames\": \"MD\",\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConvertStatus() == ConvertStatus.ERROR);
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().get(0).equalsIgnoreCase(UnitConversionImpl.ILLEGAL_PROPERTY_NAMES));
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
    }

    @Test
    public void shouldReturnOriginalRecordWhenPropertyIsMissingInData() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"TVD\": 10.0},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"propertyNames\": [\"MD\"],\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecord.setConvertStatus(ConvertStatus.SUCCESS);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConvertStatus() == ConvertStatus.SUCCESS);
        String message = String.format(UnitConversionImpl.MISSING_PROPERTY, "MD");
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().get(0).equalsIgnoreCase(message));
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
    }

    @Test
    public void shouldReturnOriginalRecordWhenPropertyValueIsNullInData() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": null},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"propertyNames\": [\"MD\"],\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecord.setConvertStatus(ConvertStatus.SUCCESS);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConvertStatus() == ConvertStatus.SUCCESS);
        String message = String.format(UnitConversionImpl.MISSING_PROPERTY, "MD");
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().get(0).equalsIgnoreCase(message));
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
    }

    @Test
    public void shouldReturnOriginalRecordWhenPropertyIsBadInData() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": \"Bad\"},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"propertyNames\": [\"MD\"],\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        JsonArray metaArray = record.getAsJsonArray("meta");
        Assert.assertEquals(1, metaArray.size());
        JsonObject meta = (JsonObject)metaArray.get(0);
        String persistableReference = meta.get("persistableReference").getAsString();
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConvertStatus() == ConvertStatus.ERROR);
        String message = String.format(UnitConversionImpl.ILLEGAL_PROPERTY_VALUE, "MD");
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().get(0).equalsIgnoreCase(message));
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
        JsonArray resultMetaArray = resultRecord.getAsJsonArray("meta");
        Assert.assertEquals(1, resultMetaArray.size());
        JsonObject resultMeta = (JsonObject)resultMetaArray.get(0);
        String resultPersistableReference = resultMeta.get("persistableReference").getAsString();
        Assert.assertTrue(persistableReference == resultPersistableReference);
    }

    @Test
    public void shouldReturnUpdatedRecordWhenUnitMetaAndDataAreValid() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": 10.0},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"propertyNames\": [\"MD\"],\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        JsonArray metaArray = record.getAsJsonArray("meta");
        Assert.assertEquals(1, metaArray.size());
        JsonObject meta = (JsonObject)metaArray.get(0);
        String persistableReference = meta.get("persistableReference").getAsString();
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().size() == 0);
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        JsonElement data = resultRecord.get("data");
        double actualMDValue = data.getAsJsonObject().get("MD").getAsDouble();
        Assert.assertEquals(3.048, actualMDValue, 0.00001);
        JsonArray resultMetaArray = resultRecord.getAsJsonArray("meta");
        Assert.assertEquals(1, resultMetaArray.size());
        JsonObject resultMeta = (JsonObject)resultMetaArray.get(0);
        String resultPersistableReference = resultMeta.get("persistableReference").getAsString();
        Assert.assertTrue(persistableReference != resultPersistableReference);
        String resultName = resultMeta.get("name").getAsString();
        Assert.assertEquals("m", resultName);
    }

    @Test
    public void shouldReturnUpdatedRecordWhenUnitMetaAndDataAreValidAndNested() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": {\"value\": 10.0}},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"propertyNames\": [\"MD.value\"],\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        JsonArray metaArray = record.getAsJsonArray("meta");
        Assert.assertEquals(1, metaArray.size());
        JsonObject meta = (JsonObject)metaArray.get(0);
        String persistableReference = meta.get("persistableReference").getAsString();
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().size() == 0);
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        JsonElement data = resultRecord.get("data");
        double actualMDValue = data.getAsJsonObject().getAsJsonObject("MD").get("value").getAsDouble();
        Assert.assertEquals(3.048, actualMDValue, 0.00001);
        JsonArray resultMetaArray = resultRecord.getAsJsonArray("meta");
        Assert.assertEquals(1, resultMetaArray.size());
        JsonObject resultMeta = (JsonObject)resultMetaArray.get(0);
        String resultPersistableReference = resultMeta.get("persistableReference").getAsString();
        Assert.assertTrue(persistableReference != resultPersistableReference);
        String resultName = resultMeta.get("name").getAsString();
        Assert.assertEquals("m", resultName);
    }

    @Test
    public void shouldReturnOriginalRecordWhenPropertyValueIsNullInDataAndNested() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": {\"value\": null}},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": \"%7B%22ScaleOffset%22%3A%7B%22Scale%22%3A0.3048%2C%22Offset%22%3A0.0%7D%2C%22Symbol%22%3A%22ft%22%2C%22BaseMeasurement%22%3A%22%257B%2522Ancestry%2522%253A%2522Length%2522%257D%22%7D\",\"propertyNames\": [\"MD.value\"],\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecord.setConvertStatus(ConvertStatus.SUCCESS);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConvertStatus() == ConvertStatus.SUCCESS);
        String message = String.format(UnitConversionImpl.MISSING_PROPERTY, "MD.value");
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().get(0).equalsIgnoreCase(message));
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        Assert.assertEquals(record, resultRecord);
    }

    @Test
    public void shouldReturnUpdatedRecordWhenPersistableReferenceIsJsonObject() {
        String stringRecord = "{\"id\": \"unit-test-1\",\"kind\": \"unit:test:1.0.0\",\"data\": {\"MD\": 10.0},\"meta\": [{\"path\": \"\",\"kind\": \"UNIT\",\"persistableReference\": { \"scaleOffset\": {\"scale\": 0.3048, \"offset\": 0 }, \"symbol\": \"ft/s\", \"baseMeasurement\": { \"type\": \"UM\", \"ancestry\": \"Velocity\" }, \"type\": \"USO\" },\"propertyNames\": [\"MD\"],\"name\": \"ft\"}]}";
        JsonObject record = (JsonObject) this.jsonParser.parse(stringRecord);
        JsonArray metaArray = record.getAsJsonArray("meta");
        Assert.assertEquals(1, metaArray.size());
        JsonObject meta = (JsonObject) metaArray.get(0);
        String persistableReference = meta.get("persistableReference").toString();
        List<ConversionRecord> conversionRecords = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        conversionRecord.setRecordJsonObject(record);
        conversionRecords.add(conversionRecord);
        this.unitConversion.convertUnitsToSI(conversionRecords);
        Assert.assertEquals(1, conversionRecords.size());
        Assert.assertTrue(conversionRecords.get(0).getConversionMessages().size() == 0);
        JsonObject resultRecord = conversionRecords.get(0).getRecordJsonObject();
        JsonElement data = resultRecord.get("data");
        double actualMDValue = data.getAsJsonObject().get("MD").getAsDouble();
        Assert.assertEquals(3.048, actualMDValue, 0.00001);
        JsonArray resultMetaArray = resultRecord.getAsJsonArray("meta");
        Assert.assertEquals(1, resultMetaArray.size());
        JsonObject resultMeta = (JsonObject) resultMetaArray.get(0);
        String resultPersistableReference = resultMeta.get("persistableReference").getAsString();
        Assert.assertTrue(persistableReference != resultPersistableReference);
        String resultName = resultMeta.get("name").getAsString();
        Assert.assertEquals("m/s", resultName);
    }

}

