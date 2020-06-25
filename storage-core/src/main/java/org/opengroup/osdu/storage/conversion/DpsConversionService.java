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
import org.opengroup.osdu.core.common.crs.dates.DatesConversionImpl;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.storage.ConversionStatus;
import org.opengroup.osdu.core.common.model.crs.ConversionRecord;
import org.opengroup.osdu.core.common.model.crs.ConvertStatus;
import org.opengroup.osdu.core.common.model.crs.RecordsAndStatuses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.opengroup.osdu.core.common.crs.UnitConversionImpl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.opengroup.osdu.core.common.model.http.AppException;

@Service
public class DpsConversionService {

    @Autowired
    private CrsConversionService crsConversionService;

    @Autowired
    private JaxRsDpsLog logger;

    private UnitConversionImpl unitConversionService = new UnitConversionImpl();
    private DatesConversionImpl datesConversionService = new DatesConversionImpl();

    private static final String CONVERSION_NO_META_BLOCK = "No Meta Block in This Record.";

    public RecordsAndStatuses doConversion(List<JsonObject> originalRecords) {
        List<ConversionStatus.ConversionStatusBuilder> conversionStatuses = new ArrayList<>();
        List<ConversionRecord> recordsWithoutMetaBlock = new ArrayList<>();
        List<JsonObject> recordsWithMetaBlock = this.classifyRecords(originalRecords, conversionStatuses,
                recordsWithoutMetaBlock);
        List<ConversionRecord> allRecords = recordsWithoutMetaBlock;
        if (conversionStatuses.size() > 0) {
            RecordsAndStatuses crsConversionResult = this.crsConversionService.doCrsConversion(recordsWithMetaBlock,
                    conversionStatuses);
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

            this.unitConversionService.convertUnitsToSI(conversionRecords);
            this.datesConversionService.convertDatesToISO(conversionRecords);
            allRecords.addAll(conversionRecords);
        }
        this.checkMismatchAndLogMissing(originalRecords, allRecords);

        return this.MakeResponseStatus(allRecords);
    }

    private List<JsonObject> classifyRecords(List<JsonObject> originalRecords, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses, List<ConversionRecord> recordsWithoutMetaBlock) {
        List<JsonObject> recordsWithMetaBlock = new ArrayList<>();
        for (int i = 0; i < originalRecords.size(); i++) {
            JsonObject recordJsonObject = originalRecords.get(i);
            if (this.isMetaBlockPresent(recordJsonObject)) {
                recordsWithMetaBlock.add(recordJsonObject);
                String recordId = this.getRecordId(recordJsonObject);
                conversionStatuses.add(ConversionStatus.builder().id(recordId).status(ConvertStatus.SUCCESS.toString()));
            } else {
                ConversionRecord conversionRecord = new ConversionRecord();
                conversionRecord.setRecordJsonObject(recordJsonObject);
                conversionRecord.setConvertStatus(ConvertStatus.NO_FRAME_OF_REFERENCE);
                List<String> conversionStatusNoMeta = new ArrayList<>();
                conversionStatusNoMeta.add(CONVERSION_NO_META_BLOCK);
                conversionRecord.setConversionMessages(conversionStatusNoMeta);
                recordsWithoutMetaBlock.add(conversionRecord);
            }
        }
        return recordsWithMetaBlock;
    }

    private boolean isMetaBlockPresent(JsonObject record) {
        boolean isPresent = true;
        JsonArray metaBlock = record.getAsJsonArray("meta");
        if (metaBlock == null || metaBlock.size() == 0) {
            isPresent = false;
        }
        return isPresent;
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
