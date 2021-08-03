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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.crs.UnitConversionImpl;
import org.opengroup.osdu.core.common.crs.dates.DatesConversionImpl;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.crs.ConversionRecord;
import org.opengroup.osdu.core.common.model.crs.ConvertStatus;
import org.opengroup.osdu.core.common.model.crs.RecordsAndStatuses;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.ConversionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

@Service
public class DpsConversionService {

    @Autowired
    private CrsConversionService crsConversionService;

    @Autowired
    private JaxRsDpsLog logger;

    private UnitConversionImpl unitConversionService = new UnitConversionImpl();
    private DatesConversionImpl datesConversionService = new DatesConversionImpl();

    private static final String NO_CONVERSION = "No Conversion Blocks or 'Wgs84Coordinates' block exists in this record.";
    public static final List<String> validAttributes = new ArrayList<>(Arrays.asList("SpatialLocation","ProjectedBottomHoleLocation","GeographicBottomHoleLocation","SpatialArea","SpatialPoint","ABCDBinGridSpatialLocation","FirstLocation","LastLocation","LiveTraceOutline"));

    public RecordsAndStatuses doConversion(List<JsonObject> originalRecords) {
        List<ConversionStatus.ConversionStatusBuilder> conversionStatuses = new ArrayList<>();
        List<JsonObject> recordsWithMetaBlock = new ArrayList<>();
        List<JsonObject> recordsWithGeoJsonBlock = new ArrayList<>();

        List<ConversionRecord> recordsWithoutConversionBlock = this.classifyRecords(originalRecords, conversionStatuses, recordsWithMetaBlock, recordsWithGeoJsonBlock);
        List<ConversionRecord> allRecords = recordsWithoutConversionBlock;

        if (conversionStatuses.size() > 0) {
            RecordsAndStatuses crsConversionResult = null;
            if (recordsWithMetaBlock.size() > 0) {
                crsConversionResult = this.crsConversionService.doCrsConversion(recordsWithMetaBlock, conversionStatuses);
                List<ConversionRecord> conversionRecords = this.getConversionRecords(crsConversionResult);
                this.unitConversionService.convertUnitsToSI(conversionRecords);
                this.datesConversionService.convertDatesToISO(conversionRecords);
                allRecords.addAll(conversionRecords);
            }
            if (recordsWithGeoJsonBlock.size() > 0) {
                crsConversionResult = this.crsConversionService.doCrsGeoJsonConversion(recordsWithGeoJsonBlock, conversionStatuses);
                List<ConversionRecord> conversionRecords = this.getConversionRecords(crsConversionResult);
                allRecords.addAll(conversionRecords);
            }
        }
        this.checkMismatchAndLogMissing(originalRecords, allRecords);

        return this.MakeResponseStatus(allRecords);
    }

    private List<ConversionRecord> classifyRecords(List<JsonObject> originalRecords, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses, List<JsonObject> recordsWithMetaBlock, List<JsonObject> recordsWithGeoJsonBlock) {
        List<ConversionRecord> recordsWithoutConversionBlock = new ArrayList<>();
        ConversionRecord conversionRecord = new ConversionRecord();
        for (int i = 0; i < originalRecords.size(); i++) {
            JsonObject recordJsonObject = originalRecords.get(i);
            if (this.isMetaBlockPresent(recordJsonObject)) {
                recordsWithMetaBlock.add(recordJsonObject);
                String recordId = this.getRecordId(recordJsonObject);
                conversionStatuses.add(ConversionStatus.builder().id(recordId).status(ConvertStatus.SUCCESS.toString()));
            } else if (this.isAsIngestedCoordinatesPresent(recordJsonObject)) {
                recordsWithGeoJsonBlock.add(recordJsonObject);
                String recordId = this.getRecordId(recordJsonObject);
                conversionStatuses.add(ConversionStatus.builder().id(recordId).status(ConvertStatus.SUCCESS.toString()));
            } else {
                conversionRecord.setRecordJsonObject(recordJsonObject);
                conversionRecord.setConvertStatus(ConvertStatus.NO_FRAME_OF_REFERENCE);
                List<String> conversionStatusNoConversionBlock = new ArrayList<>();
                conversionStatusNoConversionBlock.add(NO_CONVERSION);
                conversionRecord.setConversionMessages(conversionStatusNoConversionBlock);
                recordsWithoutConversionBlock.add(conversionRecord);
            }
        }
        return recordsWithoutConversionBlock;
    }

    private boolean isAsIngestedCoordinatesPresent(JsonObject record) {
        JsonObject filteredObject = this.filterDataFields(record,validAttributes);
        return ((filteredObject != null) && (filteredObject.size() > 0)) ? true : false;
    }

    private boolean isMetaBlockPresent(JsonObject record) {
        if (record.get(Constants.META) == null || record.get(Constants.META).isJsonNull()) {
            return false;
        }
        JsonArray metaBlock = record.getAsJsonArray(Constants.META);
        return metaBlock != null && metaBlock.size() != 0;
    }

    private String getRecordId(JsonObject record) {
        JsonElement recordId = record.get("id");
        if (recordId == null || recordId instanceof JsonNull || recordId.getAsString().isEmpty()) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown error", "record does not have id");
        }
        return recordId.getAsString();
    }

    ConversionStatus getRecordConversionStatus(List<ConversionStatus> conversionStatuses, String recordId) {
        for (int i = 0; i < conversionStatuses.size(); i++) {
            ConversionStatus conversionStatus = conversionStatuses.get(i);
            if (conversionStatus.getId().equals(recordId)) {
                return conversionStatus;
            }
        }
        return null;
    }

    private RecordsAndStatuses MakeResponseStatus(List<ConversionRecord> conversionRecords) {
        RecordsAndStatuses result = new RecordsAndStatuses();
        List<JsonObject> records = new ArrayList<>();
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        for (ConversionRecord conversionRecord : conversionRecords) {
            records.add(conversionRecord.getRecordJsonObject());
            String recordId = conversionRecord.getRecordId();
            ConversionStatus conversionStatus = new ConversionStatus();
            conversionStatus.setId(recordId);
            conversionStatus.setStatus(conversionRecord.getConvertStatus().toString());
            conversionStatus.setErrors(conversionRecord.getConversionMessages());
            conversionStatuses.add(conversionStatus);
        }
        result.setRecords(records);
        result.setConversionStatuses(conversionStatuses);
        return result;
    }

    private List<ConversionRecord> getConversionRecords(RecordsAndStatuses crsConversionResult) {
        List<JsonObject> crsConvertedRecords = crsConversionResult.getRecords();
        List<ConversionStatus> crsConversionStatuses = crsConversionResult.getConversionStatuses();

        List<ConversionRecord> conversionRecords = new ArrayList<>();
        for (int i = 0; i < crsConvertedRecords.size(); i++) {
            ConversionRecord conversionRecord = new ConversionRecord();
            conversionRecord.setRecordJsonObject(crsConvertedRecords.get(i));
            ConversionStatus conversionStatus = this.getRecordConversionStatus(crsConversionStatuses,
                    this.getRecordId(crsConvertedRecords.get(i)));
            if (conversionStatus != null) {
                conversionRecord.setConversionMessages(conversionStatus.getErrors());
                conversionRecord.setConvertStatus(ConvertStatus.valueOf(conversionStatus.getStatus()));
            }
            conversionRecords.add(conversionRecord);
        }
        return conversionRecords;
    }

    private void checkMismatchAndLogMissing(List<JsonObject> originalRecords, List<ConversionRecord> convertedRecords) {
        if (originalRecords.size() == convertedRecords.size()) {
            return;
        }

        List<String> convertedIds = convertedRecords.stream()
                .map(ConversionRecord::getRecordId).collect(Collectors.toList());

        for (JsonObject originalRecord : originalRecords) {
            String originalId = this.getRecordId(originalRecord);
            if (!convertedIds.contains(originalId) ) {
                this.logger.warning("Missing record after conversion: " + originalId);
            }
        }
    }

    public JsonObject filterDataFields(JsonObject record, List<String> attributes) {
        JsonObject dataObject = record.get(Constants.DATA).getAsJsonObject();
        JsonObject filteredData = new JsonObject();
        Iterator var = attributes.iterator();

        while (var.hasNext()) {
            String attribute = (String) var.next();
            JsonElement property = getDataSubProperty(attribute, dataObject);
            if (property != null) {
                JsonObject recordObj = property.getAsJsonObject();
                if ((recordObj.size() > 0) && (recordObj.getAsJsonObject(Constants.AS_INGESTED_COORDINATES) != null) && (recordObj.getAsJsonObject(Constants.WGS84_COORDINATES) == null)) {
                    if ((recordObj.getAsJsonObject(Constants.AS_INGESTED_COORDINATES).get(Constants.TYPE) != null) && (recordObj.getAsJsonObject(Constants.AS_INGESTED_COORDINATES).get(Constants.TYPE).getAsString().equals(Constants.ANY_CRS_FEATURE_COLLECTION))) filteredData.add(attribute, property);
                }
            }
        }
        return filteredData;
    }

    private static JsonElement getDataSubProperty(String field, JsonObject data) {
        if (field.contains(".")) {
            String[] fieldArray = field.split("\\.", 2);
            String subFieldParent = fieldArray[0];
            String subFieldChild = fieldArray[1];
            JsonElement subFieldParentElement = data.get(subFieldParent);
            if (subFieldParentElement.isJsonObject()) {
                JsonElement parentObjectValue = getDataSubProperty(subFieldChild, subFieldParentElement.getAsJsonObject());
                if (parentObjectValue != null) {
                    return parentObjectValue;
                }
            }
            return null;
        } else {
            return data.get(field);
        }
    }

}
