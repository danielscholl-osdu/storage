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

import static org.opengroup.osdu.storage.conversion.CrsConversionServiceErrorMessages.UNEXPECTED_DATA_FORMAT_JSON_OBJECT;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
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
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.storage.service.QueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DpsConversionService {
    private static final int CACHE_SIZE = 1000;

    @Value("${cache.expiration.sec:60}")
    private int cacheExpirationSec;

    @Autowired
    private QueryService queryService;

    @Autowired
    private CrsConversionService crsConversionService;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private ObjectMapper objectMapper;

    private ICache<String, Record> cache;

    @PostConstruct
    private void setup() {
        cache = new VmCache<>(cacheExpirationSec, CACHE_SIZE);
    }

    private UnitConversionImpl unitConversionService = new UnitConversionImpl();
    private DatesConversionImpl datesConversionService = new DatesConversionImpl();

    private static final List<String> validAttributes = Arrays.asList("SpatialLocation", "ProjectedBottomHoleLocation", "GeographicBottomHoleLocation", "SpatialArea", "SpatialPoint", "ABCDBinGridSpatialLocation", "FirstLocation", "LastLocation", "LiveTraceOutline");
    private static final String UNIT_OF_MEASURE_ID = "unitOfMeasureID";

    public RecordsAndStatuses doConversion(List<JsonObject> originalRecords) {
        List<ConversionStatus.ConversionStatusBuilder> conversionStatuses = new ArrayList<>();
        List<JsonObject> recordsWithMetaBlock = new ArrayList<>();
        List<JsonObject> recordsWithGeoJsonBlock = new ArrayList<>();

        List<ConversionRecord> recordsWithoutConversionBlock = this.classifyRecords(originalRecords, conversionStatuses, recordsWithMetaBlock, recordsWithGeoJsonBlock);
        Map<String, ConversionRecord> conversionResults = new HashMap<>();
        addOrUpdateRecordStatus(conversionResults, recordsWithoutConversionBlock);

        if (!conversionStatuses.isEmpty()) {
            RecordsAndStatuses crsConversionResult = null;
            if (!recordsWithGeoJsonBlock.isEmpty()) {
                List<ConversionRecord> geoConvertedRecords = new ArrayList<>();
                crsConversionResult = this.crsConversionService.doCrsGeoJsonConversion(recordsWithGeoJsonBlock, conversionStatuses);
                geoConvertedRecords = this.getConversionRecords(crsConversionResult);
                addOrUpdateRecordStatus(conversionResults, geoConvertedRecords);
            }
            if (!recordsWithMetaBlock.isEmpty()) {
                List<ConversionRecord> metaConvertedRecords = new ArrayList<>();
                crsConversionResult = this.crsConversionService.doCrsConversion(recordsWithMetaBlock, conversionStatuses);
                metaConvertedRecords = this.getConversionRecords(crsConversionResult);
                // update persistableReefrence value using unitOfMeasureID property
                this.updatePersistableReference(metaConvertedRecords);
                this.unitConversionService.convertUnitsToSI(metaConvertedRecords);
                this.datesConversionService.convertDatesToISO(metaConvertedRecords);
                addOrUpdateRecordStatus(conversionResults, metaConvertedRecords);
            }
        }
        List<ConversionRecord> out = new ArrayList<>(conversionResults.values());
        this.checkMismatchAndLogMissing(originalRecords, out);
        return this.makeResponseStatus(out);
    }

    private void addOrUpdateRecordStatus(Map<String, ConversionRecord> out, List<ConversionRecord> result) {
        for (ConversionRecord conversionRecord: result) {
            String recordId = conversionRecord.getRecordId();
            out.put(recordId, conversionRecord);
        }
    }

    private List<ConversionRecord> classifyRecords(List<JsonObject> originalRecords, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses, List<JsonObject> recordsWithMetaBlock, List<JsonObject> recordsWithGeoJsonBlock) {
        List<ConversionRecord> recordsWithoutConversionBlock = new ArrayList<>();
        for (JsonObject recordJsonObject : originalRecords) {
            String recordId = this.getRecordId(recordJsonObject);
            List<String> validationErrors = new ArrayList<>();
            boolean asIngestedCoordinateConversionRequired = this.isAsIngestedCoordinatesPresent(recordJsonObject, validationErrors);
            boolean metaBlockConversionRequired = this.isMetaBlockPresent(recordJsonObject, validationErrors);
            if (asIngestedCoordinateConversionRequired || metaBlockConversionRequired) {
                if (asIngestedCoordinateConversionRequired) {
                    recordsWithGeoJsonBlock.add(recordJsonObject);
                }

                if (metaBlockConversionRequired) {
                    recordsWithMetaBlock.add(recordJsonObject);
                }
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
        return ((filteredObject != null) && (filteredObject.size() > 0));
    }

    private boolean isMetaBlockPresent(JsonObject record, List<String> validationErrors) {
        if (record.get(Constants.META) == null || record.get(Constants.META).isJsonNull()) {
            validationErrors.add(CrsConversionServiceErrorMessages.MISSING_META_BLOCK);
            return false;
        }
        JsonArray metaBlock = record.getAsJsonArray(Constants.META);
        for (JsonElement block : metaBlock) {
            if (!block.isJsonNull()) {
                return true;
            }
        }
        validationErrors.add(CrsConversionServiceErrorMessages.MISSING_META_BLOCK);
        return false;
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

    private RecordsAndStatuses makeResponseStatus(List<ConversionRecord> conversionRecords) {
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

    private void updatePersistableReference(List<ConversionRecord> conversionRecords) {
        for (ConversionRecord conversionRecord : conversionRecords) {
            JsonObject recordObj = conversionRecord.getRecordJsonObject();
            JsonArray metaArray = recordObj.getAsJsonArray(Constants.META);
            if (metaArray == null) {
                return;
            }
            for (JsonElement item : metaArray) {
                JsonObject metaItem = (JsonObject) item;
                JsonElement unitOfMeasureIDElement = metaItem.get(UNIT_OF_MEASURE_ID);
                if (unitOfMeasureIDElement == null || unitOfMeasureIDElement.getAsString().equals("")) {
                    return;
                }
                String unitOfMeasureID = unitOfMeasureIDElement.getAsString().replaceAll(":$", "");
                String persistableReference = this.getPersistableReferenceByUnitOfMeasureID(unitOfMeasureID);
                if (persistableReference.equals("")) {
                    this.logger.warning("Persistable reference was not obtained for record %s by unit of measure %s"
                            .formatted(recordObj.get(Constants.ID), unitOfMeasureID));
                    return;
                }
                // update persistableReference to corresponding to unitOfMeasureID
                metaItem.addProperty(Constants.PERSISTABLE_REFERENCE, persistableReference);
            }
        }
    }

    private String getCacheKey(String partitionId, String recordId) {
        return String.format("%s-record-%s", partitionId, recordId);
    }

    private Record getRecordFromCache(String recordId) {
        Record recordObj = null;
        if (this.cache != null) {
            String cacheKey = this.getCacheKey(this.headers.getPartitionId(), recordId);
            recordObj = this.cache.get(cacheKey);
        }
        return recordObj;
    }

    private void putRecordToCache(String recordId, Record record) {
        if (this.cache != null) {
            String cacheKey = this.getCacheKey(this.headers.getPartitionId(), recordId);
            this.cache.put(cacheKey, record);
        }
    }

    private String getPersistableReferenceByUnitOfMeasureID(String unitOfMeasureID) {
        Record recordObj = this.getRecordFromCache(unitOfMeasureID);
        if (recordObj == null) {
            String blob;
            try {
                blob = this.queryService.getRecordInfo(unitOfMeasureID, null, Optional.<CollaborationContext>empty());
            } catch (AppException e) {
                this.logger.error(String.format("Wrong unitOfMeasureID provided: %s", unitOfMeasureID), e);
                return "";
            }
            try {
                recordObj = objectMapper.readValue(blob, Record.class);
            } catch (JsonProcessingException e) {
                this.logger.error(String.format("Error occurred during parsing record for unitOfMeasureID: %s", unitOfMeasureID), e);
                return "";
            }
            this.putRecordToCache(unitOfMeasureID, recordObj);
        }
        Map<String, Object> recordData = recordObj.getData();
        if (recordData == null) {
            return "";
        }
        Object persistableReference = recordData.get(StringUtils.capitalize(Constants.PERSISTABLE_REFERENCE));
        if (persistableReference == null) {
            return "";
        }
        return persistableReference.toString();
    }

    private void checkMismatchAndLogMissing(List<JsonObject> originalRecords, List<ConversionRecord> convertedRecords) {
        if (originalRecords.size() == convertedRecords.size()) {
            return;
        }

        List<String> convertedIds = convertedRecords.stream()
                .map(ConversionRecord::getRecordId).collect(Collectors.toList());

        for (JsonObject originalRecord : originalRecords) {
            String originalId = this.getRecordId(originalRecord);
            if (!convertedIds.contains(originalId)) {
                this.logger.warning("Missing record after conversion: " + originalId);
            }
        }
    }

    public JsonObject filterDataFields(JsonObject record, List<String> validationErrors) {
        JsonObject dataObject = record.get(Constants.DATA).getAsJsonObject();
        JsonObject filteredData = new JsonObject();
        Iterator var = validAttributes.iterator();

        if (dataObject == null) {
            validationErrors.add(CrsConversionServiceErrorMessages.MISSING_DATA_BLOCK);
            return null;
        }

        while (var.hasNext()) {
            String attribute = (String) var.next();
            JsonElement property = getDataSubProperty(attribute, dataObject);
            if (property == null || property instanceof JsonNull) continue;

            if (!property.isJsonObject()) {
                validationErrors.add(String.format(UNEXPECTED_DATA_FORMAT_JSON_OBJECT, attribute));
                continue;
            }

            JsonObject recordObj = property.getAsJsonObject();
            if (recordObj.has(Constants.WGS84_COORDINATES) && !recordObj.get(Constants.WGS84_COORDINATES).isJsonNull()) {
                validationErrors.add(CrsConversionServiceErrorMessages.WGS84COORDINATES_EXISTS);
                continue;
            }

            if ((recordObj.size() == 0) || !recordObj.has(Constants.AS_INGESTED_COORDINATES) || recordObj.get(Constants.AS_INGESTED_COORDINATES).isJsonNull()) {
                validationErrors.add(CrsConversionServiceErrorMessages.MISSING_AS_INGESTED_COORDINATES);
                continue;
            }

            JsonObject asIngestedCoordinates = recordObj.getAsJsonObject(Constants.AS_INGESTED_COORDINATES);
            String type = asIngestedCoordinates.has(Constants.TYPE) && !asIngestedCoordinates.get(Constants.TYPE).isJsonNull()
                    ? asIngestedCoordinates.get(Constants.TYPE).getAsString() : "";
            if (type.equals(Constants.ANY_CRS_FEATURE_COLLECTION)) {
                filteredData.add(attribute, property);
            } else {
                validationErrors.add(String.format(CrsConversionServiceErrorMessages.MISSING_AS_INGESTED_TYPE, type));
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
