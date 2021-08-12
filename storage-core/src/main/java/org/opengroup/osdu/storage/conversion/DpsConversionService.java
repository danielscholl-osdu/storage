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

import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.*;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.crs.CrsConversionServiceErrorMessages;
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

@Service
public class DpsConversionService {

    @Autowired
    private CrsConversionService crsConversionService;

    @Autowired
    private JaxRsDpsLog logger;

    private UnitConversionImpl unitConversionService = new UnitConversionImpl();
    private DatesConversionImpl datesConversionService = new DatesConversionImpl();

    public static final List<String> validAttributes = Arrays.asList("SpatialLocation","ProjectedBottomHoleLocation","GeographicBottomHoleLocation","SpatialArea","SpatialPoint","ABCDBinGridSpatialLocation","FirstLocation","LastLocation","LiveTraceOutline");

    public RecordsAndStatuses doConversion(List<JsonObject> originalRecords) {
        List<ConversionStatus.ConversionStatusBuilder> conversionStatuses = new ArrayList<>();
        List<JsonObject> recordsWithMetaBlock = new ArrayList<>();
        List<JsonObject> recordsWithGeoJsonBlock = new ArrayList<>();

        List<ConversionRecord> recordsWithoutConversionBlock = this.classifyRecords(originalRecords, conversionStatuses, recordsWithMetaBlock, recordsWithGeoJsonBlock);
        List<ConversionRecord> allRecords = recordsWithoutConversionBlock;

        if (!conversionStatuses.isEmpty()) {
            RecordsAndStatuses crsConversionResult = null;
            if (recordsWithGeoJsonBlock.size() > 0) {
                crsConversionResult = this.crsConversionService.doCrsGeoJsonConversion(recordsWithGeoJsonBlock, conversionStatuses);
                List<ConversionRecord> conversionRecords = this.getConversionRecords(crsConversionResult);
                allRecords.addAll(conversionRecords);
            }
            if (recordsWithMetaBlock.size() > 0) {
                crsConversionResult = this.crsConversionService.doCrsConversion(recordsWithMetaBlock, conversionStatuses);
                List<ConversionRecord> conversionRecords = this.getConversionRecords(crsConversionResult);
                this.unitConversionService.convertUnitsToSI(conversionRecords);
                this.datesConversionService.convertDatesToISO(conversionRecords);
                allRecords.addAll(conversionRecords);
            }
        }
        this.checkMismatchAndLogMissing(originalRecords, allRecords);

        return this.MakeResponseStatus(allRecords);
    }

    private List<ConversionRecord> classifyRecords(List<JsonObject> originalRecords, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses, List<JsonObject> recordsWithMetaBlock, List<JsonObject> recordsWithGeoJsonBlock) {
        List<ConversionRecord> recordsWithoutConversionBlock = new ArrayList<>();
        for (JsonObject recordJsonObject : originalRecords) {
            String recordId = this.getRecordId(recordJsonObject);
            List<String> validationErrors = new ArrayList<>();
            if (this.isAsIngestedCoordinatesPresent(recordJsonObject, validationErrors)) {
                recordsWithGeoJsonBlock.add(recordJsonObject);
                conversionStatuses.add(ConversionStatus.builder().id(recordId).status(ConvertStatus.SUCCESS.toString()));
            } else if (this.isMetaBlockPresent(recordJsonObject, validationErrors)) {
                recordsWithMetaBlock.add(recordJsonObject);
                conversionStatuses.add(ConversionStatus.builder().id(recordId).status(ConvertStatus.SUCCESS.toString()));
            } else {
                ConversionRecord conversionRecord = new ConversionRecord();
                conversionRecord.setRecordJsonObject(recordJsonObject);
                conversionRecord.setConvertStatus(ConvertStatus.NO_FRAME_OF_REFERENCE);
                conversionRecord.setConversionMessages(validationErrors);
                recordsWithoutConversionBlock.add(conversionRecord);
            }
        }
        return recordsWithoutConversionBlock;
    }

    private boolean isAsIngestedCoordinatesPresent(JsonObject record, List<String> validationErrors) {
        JsonObject filteredObject = this.filterDataFields(record, validationErrors);
        return ((filteredObject != null) && (filteredObject.size() > 0)) ? true : false;
    }

    private boolean isMetaBlockPresent(JsonObject record, List<String> validationErrors) {
        if (record.get(Constants.META) == null || record.get(Constants.META).isJsonNull()) {
            validationErrors.add(CrsConversionServiceErrorMessages.MISSING_META_BLOCK);
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
        for (JsonObject conversionRecord : crsConvertedRecords) {
            ConversionRecord ConversionRecordObj = new ConversionRecord();
            ConversionRecordObj.setRecordJsonObject(conversionRecord);
            ConversionStatus conversionStatus = this.getRecordConversionStatus(crsConversionStatuses,
                    this.getRecordId(conversionRecord));
            if (conversionStatus != null) {
                ConversionRecordObj.setConversionMessages(conversionStatus.getErrors());
                ConversionRecordObj.setConvertStatus(ConvertStatus.valueOf(conversionStatus.getStatus()));
            }
            conversionRecords.add(ConversionRecordObj);
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

    public JsonObject filterDataFields(JsonObject record, List<String> validationErrors) {
        JsonObject dataObject = record.get(Constants.DATA).getAsJsonObject();
        JsonObject filteredData = new JsonObject();
        Iterator var = validAttributes.iterator();

        while (var.hasNext()) {
            String attribute = (String) var.next();
            JsonElement property = getDataSubProperty(attribute, dataObject);
            if (property != null) {
                JsonObject recordObj = property.getAsJsonObject();

                if ((recordObj.getAsJsonObject(Constants.WGS84_COORDINATES) == null)) {
                    if ((recordObj.size() > 0) && (recordObj.getAsJsonObject(Constants.AS_INGESTED_COORDINATES) != null)) {
                        String type = ((recordObj.getAsJsonObject(Constants.AS_INGESTED_COORDINATES).has(Constants.TYPE)) && (!recordObj.getAsJsonObject(Constants.AS_INGESTED_COORDINATES).get(Constants.TYPE).isJsonNull())) ? recordObj.getAsJsonObject(Constants.AS_INGESTED_COORDINATES).get(Constants.TYPE).getAsString() : "";
                        if (type.equals(Constants.ANY_CRS_FEATURE_COLLECTION)) {
                            filteredData.add(attribute, property);
                        } else {
                            validationErrors.add(String.format(CrsConversionServiceErrorMessages.MISSING_AS_INGESTED_TYPE, type));
                        }
                    }else {
                        validationErrors.add(CrsConversionServiceErrorMessages.MISSING_AS_INGESTED_COORDINATES);
                    }
                } else {
                    validationErrors.add(CrsConversionServiceErrorMessages.WGS84COORDINATES_EXISTS);
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
