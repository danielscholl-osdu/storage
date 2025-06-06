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

package org.opengroup.osdu.storage.service;

import com.google.common.base.Strings;
import com.google.gson.*;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.crs.CrsConverterClientFactory;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.crs.RecordsAndStatuses;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.storage.PersistenceHelper;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.opengroup.osdu.storage.conversion.DpsConversionService;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.opengroup.osdu.storage.util.RecordConstants.OPA_FEATURE_NAME;


public abstract class BatchServiceImpl implements BatchService {

    private static final String FRAME_OF_REF_HEADER = "frame-of-reference";
    private static final String NO_FRAME_OF_REFERENCE = "none";
    private static final String SI_FRAME_OF_REFERENCE = "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;";

    @Autowired
    private IRecordsMetadataRepository recordRepository;

    @Autowired
    private ICloudStorage cloudStorage;

    @Autowired
    private StorageAuditLogger auditLogger;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private DpsConversionService conversionService;

    @Autowired
    private CrsConverterClientFactory crsConverterClientFactory;

    @Autowired
    private IEntitlementsExtensionService entitlementsAndCacheService;

    @Autowired
    private IOPAService opaService;

    @Autowired
    private IFeatureFlag featureFlag;

    public final Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

    @Override
    public MultiRecordInfo getMultipleRecords(MultiRecordIds ids, Optional<CollaborationContext> collaborationContext) {

        List<String> recordIds = ids.getRecords();
        Map<String, String> validRecords = new HashMap<>();
        List<String> recordsNotFound = new ArrayList<>();
        List<String> retryRecords = new ArrayList<>();

        Map<String, RecordMetadata> recordsMetadata = this.recordRepository.get(recordIds, collaborationContext);

        for (String recordId : recordIds) {
            RecordMetadata recordMetadata = recordsMetadata.get(CollaborationContextUtil.composeIdWithNamespace(recordId, collaborationContext));

            if (recordMetadata == null || !recordMetadata.getStatus().equals(RecordState.active)) {
                recordsNotFound.add(recordId);
                continue;
            }

            Long latestVersion = recordMetadata.getLatestVersion();
            if (latestVersion == null) {
                recordsNotFound.add(recordId);
                continue;
            }

            validRecords.put(recordId, recordMetadata.getVersionPath(latestVersion));
        }

        List<String> validRecordObjects = new ArrayList<>(validRecords.values());
        List<Record> recordObjects = new ArrayList<>();
        if (validRecordObjects.isEmpty()) {
            MultiRecordInfo response = new MultiRecordInfo();
            response.setInvalidRecords(recordsNotFound);
            response.setRecords(recordObjects);
            response.setRetryRecords(retryRecords);
            return response;
        }

        Map<String, String> recordsPreAclMap = this.cloudStorage.read(validRecords, collaborationContext);

        this.logUnauthorizedGCSRecords(validRecords, recordsPreAclMap);
        Map<String, String> recordsMap = this.postCheckRecordsAcl(recordsPreAclMap, recordsMetadata, collaborationContext);
        this.auditLogger.readMultipleRecordsSuccess(validRecordObjects);

        validRecordObjects.clear();

        List<String> validAttributes = PersistenceHelper.getValidRecordAttributes(ids.getAttributes());

        JsonParser jsonParser = new JsonParser();

        recordsMap.keySet().forEach(recordId -> {
            String recordData = recordsMap.get(recordId);

            if (Strings.isNullOrEmpty(recordData)) {
                retryRecords.add(recordId);
            } else {
                JsonElement jsonRecord = jsonParser.parse(recordData);

                // Filter out data sub properties
                if (!validAttributes.isEmpty()) {
                    jsonRecord = PersistenceHelper.filterRecordDataFields(jsonRecord, validAttributes);
                }

                RecordMetadata recordMetadata = recordsMetadata.get(CollaborationContextUtil.composeIdWithNamespace(recordId, collaborationContext));
                JsonObject recordObject = PersistenceHelper.combineRecordMetaDataAndRecordDataIntoJsonObject(jsonRecord, recordMetadata, recordMetadata.getLatestVersion());

                Record record = gson.fromJson(recordObject, Record.class);

                recordObjects.add(record);
            }
        });

        MultiRecordInfo response = new MultiRecordInfo();
        response.setInvalidRecords(recordsNotFound);
        response.setRecords(recordObjects);
        response.setRetryRecords(retryRecords);

        return response;
    }

    @Override
    public MultiRecordResponse fetchMultipleRecords(MultiRecordRequest ids, Optional<CollaborationContext> collaborationContext) {
        String frameOfRef = this.headers.getHeaders().get(FRAME_OF_REF_HEADER);
        // TODO:
        // it appears FRAME_OF_REF_HEADER is required to even set isConversionNeeded to false
        // but this header is not recognized in client lib DpsHeaders and can't be set.
        // verify what should be the right behavior
        boolean isConversionNeeded = true;
        if (frameOfRef == null || (frameOfRef.equalsIgnoreCase(NO_FRAME_OF_REFERENCE)) ||
                //TODO: remove when converter service is available in all clouds
                (Strings.isNullOrEmpty(crsConverterClientFactory.crsApi))) {
            isConversionNeeded = false;
        } else if (!frameOfRef.equalsIgnoreCase(SI_FRAME_OF_REFERENCE)) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Frame of reference is not appropriately provided",
                    "please use customized header frame-of-reference and either 'none' or 'units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc' would be valid");
        }

        MultiRecordResponse response = new MultiRecordResponse();
        Map<String, String> validRecords = new HashMap<>();
        List<String> recordsNotFound = new ArrayList<>();
        List<ConversionStatus> conversionStatuses = new ArrayList<>();

        List<String> recordIds = ids.getRecords();
        Map<String, RecordMetadata> recordsMetadata = this.recordRepository.get(recordIds, collaborationContext);

        for (String recordId : recordIds) {
            RecordMetadata recordMetadata = recordsMetadata.get(CollaborationContextUtil.composeIdWithNamespace(recordId, collaborationContext));
            if (recordMetadata == null || !recordMetadata.getStatus().equals(RecordState.active) || recordMetadata.getLatestVersion() == null) {
                recordsNotFound.add(recordId);
                continue;
            }
            validRecords.put(recordId, recordMetadata.getVersionPath(recordMetadata.getLatestVersion()));
        }
        if (!recordsNotFound.isEmpty()) {
            logger.error("Records were not found due to missed record metadata: " + recordsNotFound);
        }

        List<String> validRecordObjects = new ArrayList<>(validRecords.values());
        if (validRecordObjects.isEmpty()) {
            response.setRecords(validRecordObjects);
            response.setNotFound(recordsNotFound);
            response.setConversionStatuses(conversionStatuses);
            return response;
        }

        List<String> recordsNotFoundInCloudStorage = new ArrayList<>();
        Map<String, String> recordsPreAclMap = this.cloudStorage.read(validRecords, collaborationContext);
        this.logUnauthorizedGCSRecords(validRecords, recordsPreAclMap);
        Map<String, String> recordsFromCloudStorage = this.postCheckRecordsAcl(recordsPreAclMap, recordsMetadata, collaborationContext);

        this.auditLogger.readMultipleRecordsSuccess(validRecordObjects);

        List<JsonObject> jsonObjectRecords = new ArrayList<>();
        JsonParser jsonParser = new JsonParser();
        recordsFromCloudStorage.keySet().forEach(recordId -> {
            String recordData = recordsFromCloudStorage.get(recordId);
            if (Strings.isNullOrEmpty(recordData)) {
                recordsNotFoundInCloudStorage.add(recordId);
            } else {
                JsonElement jsonRecord = jsonParser.parse(recordData);
                RecordMetadata recordMetadata = recordsMetadata.get(CollaborationContextUtil.composeIdWithNamespace(recordId, collaborationContext));
                JsonObject recordJsonObject = PersistenceHelper.combineRecordMetaDataAndRecordDataIntoJsonObject(
                        jsonRecord, recordMetadata, recordMetadata.getLatestVersion());
                jsonObjectRecords.add(recordJsonObject);
            }
        });

        if (!recordsNotFoundInCloudStorage.isEmpty()) {
            logger.error("Records were not found in cloud storage: " + recordsNotFoundInCloudStorage);
            recordsNotFound.addAll(recordsNotFoundInCloudStorage);
        }

        if (isConversionNeeded && !validRecords.isEmpty()) {
            RecordsAndStatuses recordsAndStatuses = this.conversionService.doConversion(jsonObjectRecords);
            this.checkMismatchAndAddToNotFound(recordIds, recordsNotFound, recordsAndStatuses.getRecords());
            response.setConversionStatuses(recordsAndStatuses.getConversionStatuses());
            response.setRecords(this.convertFromJsonObjectListToStringList(recordsAndStatuses.getRecords()));
            response.setNotFound(recordsNotFound);
        } else {
            this.checkMismatchAndAddToNotFound(recordIds, recordsNotFound, jsonObjectRecords);
            response.setConversionStatuses(conversionStatuses);
            response.setRecords(this.convertFromJsonObjectListToStringList(jsonObjectRecords));
            response.setNotFound(recordsNotFound);
            this.auditLog(validRecordObjects, this.auditLogger::readMultipleRecordsWithOptionalConversionSuccess,
                    recordsNotFound, this.auditLogger::readMultipleRecordsWithOptionalConversionFail);
        }

        if (!recordsNotFound.isEmpty()) {
            logger.error("Records were not found in total: " + recordsNotFound);
        }
        return response;
    }

    private List<String> convertFromJsonObjectListToStringList(List<JsonObject> jsonObjectRecords) {
        List<String> records = new ArrayList<>();
        for (JsonObject recordJsonObject : jsonObjectRecords) {
            records.add(recordJsonObject.toString());
        }
        return records;
    }

    private Map<String, String> postCheckRecordsAcl(Map<String, String> recordsPreAclMap, Map<String, RecordMetadata> recordsMetadata, Optional<CollaborationContext> collaborationContext) {
        Map<String, String> recordsMap = new HashMap<>();
        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        for (Map.Entry<String, String> record : recordsPreAclMap.entrySet()) {
            RecordMetadata recordMetadata = recordsMetadata.get(CollaborationContextUtil.composeIdWithNamespace(record.getKey(), collaborationContext));
            recordMetadataList.add(recordMetadata);
        }

        if (this.entitlementsAndCacheService.isDataManager(this.headers)) {
            for (RecordMetadata metadata : recordMetadataList) {
                String recordId = metadata.getId();
                String recordData = recordsPreAclMap.get(recordId);
                recordsMap.put(recordId, recordData);
            }
        } else {
            if (featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)) {
                List<ValidationOutputRecord> dataAuthResult = this.opaService.validateUserAccessToRecords(recordMetadataList, OperationType.view);
                for (ValidationOutputRecord outputRecord : dataAuthResult) {
                    if (outputRecord.getErrors().isEmpty()) {
                        String recordId = outputRecord.getId();
                        String recordData = recordsPreAclMap.get(recordId);
                        recordsMap.put(recordId, recordData);
                    }
                }
            } else {
                List<RecordMetadata> passAclCheckRecordsMetadata = this.entitlementsAndCacheService.hasValidAccess(recordMetadataList, this.headers);
                for (RecordMetadata metadata : passAclCheckRecordsMetadata) {
                    String recordId = metadata.getId();
                    String recordData = recordsPreAclMap.get(recordId);
                    recordsMap.put(recordId, recordData);
                }
            }
        }

        return recordsMap;
    }

    private void logUnauthorizedGCSRecords(Map<String, String> validRecords, Map<String, String> recordsPreAclMap) {
        for (Map.Entry<String, String> record : validRecords.entrySet()) {
            String recordId = record.getKey();

            if (recordsPreAclMap.get(recordId) == null) {
                this.logger.warning("User not in storage object ACL: " + recordId);
            }
        }
    }

    private void checkMismatchAndAddToNotFound(List<String> requestIds, List<String> notFoundIds, List<JsonObject> fetchedRecords) {
        if (notFoundIds.size() + fetchedRecords.size() == requestIds.size()) {
            return;
        }

        List<String> fetchedIds = fetchedRecords.stream().map(this::getRecordId).collect(Collectors.toList());

        for (String requestId : requestIds) {
            if (!notFoundIds.contains(requestId) && !fetchedIds.contains(requestId)) {
                this.logger.error("Missing record when fetch records, adding to not found: " + requestId);
                notFoundIds.add(requestId);
            }
        }
    }

    private String getRecordId(JsonObject record) {
        JsonElement recordId = record.get("id");
        if (recordId == null || recordId instanceof JsonNull || recordId.getAsString().isEmpty()) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown error", "record does not have id");
        }
        return recordId.getAsString();
    }

    private void auditLog(List<String> successfulRecords, Consumer<List<String>> loggerConsumerSuccess, List<String> failedRecords, Consumer<List<String>> loggerConsumerFail) {
        if (successfulRecords != null && !successfulRecords.isEmpty()) {
            loggerConsumerSuccess.accept(successfulRecords);
        }
        if (failedRecords != null && !failedRecords.isEmpty()) {
            loggerConsumerFail.accept(failedRecords);
        }
    }
}
