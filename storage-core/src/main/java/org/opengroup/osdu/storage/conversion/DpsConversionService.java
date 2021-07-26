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
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
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

    private static final String META = "meta";
    private static final String DATA = "data";
    private static final String SPATIAL_LOCATION = "SpatialLocation";
    private static final String AS_INGESTED_COORDINATES = "AsIngestedCoordinates";
    private static final String WGS84_COORDINATES = "Wgs84Coordinates";
    private static final String CONVERSION_NO_META_BLOCK = "No Conversion Blocks exist in This Record.";

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
                ConversionRecord conversionRecord = new ConversionRecord();
                conversionRecord.setRecordJsonObject(recordJsonObject);
                conversionRecord.setConvertStatus(ConvertStatus.NO_FRAME_OF_REFERENCE);
                List<String> conversionStatusNoConversionBlock = new ArrayList<>();
                conversionStatusNoConversionBlock.add(CONVERSION_NO_META_BLOCK);
                conversionRecord.setConversionMessages(conversionStatusNoConversionBlock);
                recordsWithoutConversionBlock.add(conversionRecord);
            }
        }
        return recordsWithoutConversionBlock;
    }

    private boolean isMetaBlockPresent(JsonObject record) {
        if (record.get(META) == null || record.get(META).isJsonNull()) {
            return false;
        }
        JsonArray metaBlock = record.getAsJsonArray(META);
        return metaBlock != null && metaBlock.size() != 0;
    }

    private boolean isAsIngestedCoordinatesPresent(JsonObject record) {
        JsonObject asIngestedBlock = null;
        if (record.getAsJsonObject(DATA).getAsJsonObject(SPATIAL_LOCATION) == null || record.getAsJsonObject(DATA).getAsJsonObject(SPATIAL_LOCATION).isJsonNull()) {
            return false;
        } else {
            JsonObject spatialLocation = record.getAsJsonObject(DATA).getAsJsonObject(SPATIAL_LOCATION);
            if ((spatialLocation.getAsJsonObject(AS_INGESTED_COORDINATES) == null || spatialLocation.getAsJsonObject(AS_INGESTED_COORDINATES).isJsonNull()) && (!spatialLocation.getAsJsonObject(WGS84_COORDINATES).isJsonNull())) {
                return false;
            }
            asIngestedBlock = record.getAsJsonObject(DATA).getAsJsonObject(SPATIAL_LOCATION).getAsJsonObject(AS_INGESTED_COORDINATES);
        }
        return asIngestedBlock != null && asIngestedBlock.size() != 0;
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
}
