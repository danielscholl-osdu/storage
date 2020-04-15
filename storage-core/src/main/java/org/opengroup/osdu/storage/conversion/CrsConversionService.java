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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opengroup.osdu.core.common.model.crs.*;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.crs.ICrsConverterFactory;
import org.opengroup.osdu.core.common.crs.ICrsConverterService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.ConversionStatus;

@Service
public class CrsConversionService {
    private static final String KIND = "kind";
    private static final String CRS = "crs";
    private static final String META = "meta";
    private static final String DATA = "data";
    private static final String POINTS = "points";
    private static final String PROPERTY_NAMES = "propertyNames";
    private static final String PERSISTABLE_REFERENCE = "persistableReference";
    private static final String TO_CRS = "{\"wkt\":\"GEOGCS[\\\"GCS_WGS_1984\\\",DATUM[\\\"D_WGS_1984\\\",SPHEROID[\\\"WGS_1984\\\",6378137.0,298.257223563]],PRIMEM[\\\"Greenwich\\\",0.0],UNIT[\\\"Degree\\\",0.0174532925199433],AUTHORITY[\\\"EPSG\\\",4326]]\",\"ver\":\"PE_10_3_1\",\"name\":\"GCS_WGS_1984\",\"authCode\":{\"auth\":\"EPSG\",\"code\":\"4326\"},\"type\":\"LBC\"}";
    private static final String UNKNOWN_ERROR = "unknown error";
    private static final String CONVERSION_FAILURE = "CRS Conversion Error: Point Converted failed(CRS Converter is returning null), no conversion applied.";

    @Autowired
    private CrsPropertySet crsPropertySet;

    @Autowired
    private ICrsConverterFactory crsConverterFactory;

    @Autowired
    private DpsHeaders dpsHeaders;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private IServiceAccountJwtClient jwtClient;

    public RecordsAndStatuses doCrsConversion(List<JsonObject> originalRecords, List<ConversionStatus> conversionStatuses) {
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        Map<String, List<PointConversionInfo>> pointConversionInfoList = this.gatherCrsConversionData(originalRecords, conversionStatuses);

        if (pointConversionInfoList.isEmpty()) {
            crsConversionResult.setRecords(originalRecords);
            crsConversionResult.setConversionStatuses(conversionStatuses);
            return crsConversionResult;
        }

        List<PointConversionInfo> convertedPointsInfo = this.callClientLibraryDoConversion(pointConversionInfoList, conversionStatuses);

        for (PointConversionInfo convertedInfo: convertedPointsInfo) {
            JsonObject record = originalRecords.get(convertedInfo.getRecordIndex());
            this.updateValuesInRecord(record, convertedInfo, conversionStatuses);
            originalRecords.set(convertedInfo.getRecordIndex(), record);
        }
        crsConversionResult.setConversionStatuses(conversionStatuses);
        crsConversionResult.setRecords(originalRecords);
        return crsConversionResult;
    }

    private Map<String, List<PointConversionInfo>> gatherCrsConversionData(List<JsonObject> originalRecords, List<ConversionStatus> conversionStatuses) {
        Map<String, List<PointConversionInfo>> batchPointConversionMap = new HashMap<>();

        for (int i = 0; i < originalRecords.size(); i++) {
            JsonObject recordJsonObject = originalRecords.get(i);
            String recordId = this.getRecordId(recordJsonObject);
            JsonObject dataBlcok = recordJsonObject.getAsJsonObject(DATA);
            if (dataBlcok == null) {
                List<String> errorMsg = new ArrayList<>();
                errorMsg.add(CrsConversionServiceErrorMessages.MISSING_DATA_BLOCK);
                this.addConversionInfoToStatuses(errorMsg, recordId, conversionStatuses, true);
                continue;
            }

            List<JsonObject> metaBlocks = this.extractValidMetaItemsFromRecord(recordJsonObject, recordId, conversionStatuses);
            for (int j = 0; j < metaBlocks.size(); j++) {
                JsonObject metaBlock = metaBlocks.get(j);
                if (!metaBlock.get(KIND).getAsString().equalsIgnoreCase(CRS)) {
                    continue;
                }
                List<PointConversionInfo> pointConversionInfoList = this.constructPointConversionInfoList(originalRecords, recordId, metaBlock, i, batchPointConversionMap, dataBlcok, j, metaBlocks, conversionStatuses);
                for (PointConversionInfo pointConversionInfo : pointConversionInfoList) {
                    if (pointConversionInfo != null && !pointConversionInfo.getConversionStatus().isEmpty()) {
                        this.addConversionInfoToStatuses(pointConversionInfo.getConversionStatus(), recordId, conversionStatuses, true);
                    }
                }
            }
        }
        return batchPointConversionMap;
    }

    private List<JsonObject> extractValidMetaItemsFromRecord(JsonObject recordJsonObject, String recordId, List<ConversionStatus> conversionStatuses) {
        List<String> metaItemErrors = new ArrayList<>();
        List<JsonObject> validMetaItems = new ArrayList<>();
        try {
            JsonArray metaItemsArray = recordJsonObject.getAsJsonArray(META);
            for (int i = 0; i < metaItemsArray.size(); i++) {
                boolean addToValidMetaItems = true;
                JsonObject metaItem = metaItemsArray.get(i).getAsJsonObject();
                JsonElement kind = metaItem.get(KIND);
                if (kind == null || kind.getAsString().isEmpty()) {
                    metaItemErrors.add(CrsConversionServiceErrorMessages.MISSING_META_KIND);
                    continue;
                }
                JsonElement propertyNamesElement = metaItem.get(PROPERTY_NAMES);
                JsonArray propertyNames = null;
                try {
                    propertyNames = propertyNamesElement.getAsJsonArray();
                } catch(IllegalStateException ex){
                    metaItemErrors.add(CrsConversionServiceErrorMessages.ILLEGAL_PROPERTY_NAMES);
                    addToValidMetaItems = false;
                }

                if (propertyNames == null || propertyNames.size() == 0) {
                    metaItemErrors.add(CrsConversionServiceErrorMessages.MISSING_PROPERTY_NAMES);
                    addToValidMetaItems = false;
                }

                JsonElement persistableReferenceElement = metaItem.get(PERSISTABLE_REFERENCE);
                if (persistableReferenceElement == null || persistableReferenceElement.getAsString().isEmpty()) {
                    metaItemErrors.add(CrsConversionServiceErrorMessages.MISSING_REFERENCE);
                    addToValidMetaItems = false;
                }
                if (addToValidMetaItems) {
                    validMetaItems.add(metaItem);
                }
            }
        } catch (Exception e) {
            metaItemErrors.add(String.format(CrsConversionServiceErrorMessages.ILLEGAL_METAITEM_ARRAY, e.getMessage()));
            this.addConversionInfoToStatuses(metaItemErrors, recordId, conversionStatuses, true);
            return validMetaItems;
        }
        if (!metaItemErrors.isEmpty()) {
            this.addConversionInfoToStatuses(metaItemErrors, recordId, conversionStatuses, true);
        }
        return validMetaItems;
    }

    private List<PointConversionInfo> constructPointConversionInfoList(List<JsonObject> originalRecords, String recordId, JsonObject metaItem, int recordIndex, Map<String, List<PointConversionInfo>> mapOfPoints, JsonObject dataBlock, int metaItemIndex, List<JsonObject> metaBlocks, List<ConversionStatus> conversionStatuses) {
        List<PointConversionInfo> pointConversionInfoList = new ArrayList<>();
        String persistableReference = metaItem.get(PERSISTABLE_REFERENCE).getAsString();
        JsonArray propertyNamesArray = metaItem.get(PROPERTY_NAMES).getAsJsonArray();
        List<String> propertyNames = this.convertPropertyNamesToStringList(propertyNamesArray);
        List<String> propertyNamesRemain = new ArrayList<>();
        for (String name: propertyNames) {
            propertyNamesRemain.add(name.toLowerCase());
        }
        int propertySize = propertyNames.size();

        // nested property
        if (propertySize == 1) {
            PointConversionInfo pointConversionInfo = this.initializePoint(recordIndex, recordId, metaItemIndex, metaBlocks);
            pointConversionInfoList.add(this.crsConversionWithNestedPropertyNames(originalRecords, persistableReference, dataBlock, propertyNamesArray, pointConversionInfo, metaBlocks));
            return pointConversionInfoList;
        }

        Map<String, String> propertyPairingMap = this.crsPropertySet.getPropertyPairing();
        for (int i = 0; i < propertyNames.size(); i++) {
            String propertyX = propertyNames.get(i);
            if (propertyPairingMap.get(propertyX.toLowerCase() ) == null) {
                // either an y property or an unsupported property
                continue;
            } else {
                // find a pair of x,y
                String propertyY = propertyPairingMap.get(propertyX.toLowerCase());
                if (propertyNamesRemain.contains(propertyY)) {
                    propertyY = this.getCaseSensitivePropertyY(propertyNames, propertyY);
                    PointConversionInfo pointConversionInfo = this.initializePoint(recordIndex, recordId, metaItemIndex, metaBlocks);

                    pointConversionInfo.setXFieldName(propertyX);
                    pointConversionInfo.setYFieldName(propertyY);
                    pointConversionInfo.setXValue(this.extractPropertyFromDataBlock(dataBlock, propertyX, pointConversionInfo));
                    pointConversionInfo.setYValue(this.extractPropertyFromDataBlock(dataBlock, propertyY, pointConversionInfo));
                    pointConversionInfo.setZFieldName("Z");
                    pointConversionInfo.setZValue(0.0);
                    pointConversionInfoList.add(pointConversionInfo);

                    if (pointConversionInfo.getConversionStatus().isEmpty()) {
                        this.addPointConversionInfoIntoConversionMap(persistableReference, pointConversionInfo, mapOfPoints);
                    }

                    propertyNamesRemain.remove(propertyX.toLowerCase());
                    propertyNamesRemain.remove(propertyY.toLowerCase());
                } else {
                    continue;
                }
            }
        }
        if (!propertyNamesRemain.isEmpty()) {
            List<String> errorMsg = new ArrayList<>();
            for (String name : propertyNamesRemain) {
                errorMsg.add(String.format(CrsConversionServiceErrorMessages.PAIR_FAILURE, name));
            }
            this.addConversionInfoToStatuses(errorMsg, recordId, conversionStatuses, false);
        }
        return pointConversionInfoList;
    }

    private PointConversionInfo initializePoint(int recordIndex, String recordId, int metaItemIndex, List<JsonObject> metaBlocks) {
        PointConversionInfo pointConversionInfo = new PointConversionInfo();
        List<String> conversionStatusForAPoint = new ArrayList<>();
        pointConversionInfo.setConversionStatus(conversionStatusForAPoint);
        pointConversionInfo.setRecordIndex(recordIndex);
        pointConversionInfo.setRecordId(recordId);
        pointConversionInfo.setMetaItemIndex(metaItemIndex);
        pointConversionInfo.setMetaItems(metaBlocks);
        pointConversionInfo.setHasError(false);

        return pointConversionInfo;
    }

    private List<String> convertPropertyNamesToStringList(JsonArray propertyNamesJsonArray) {
        List<String> propertyNames = new ArrayList<>();
        for (JsonElement p : propertyNamesJsonArray) {
            propertyNames.add(p.getAsString());
        }
        return propertyNames;
    }

    private double extractPropertyFromDataBlock(JsonObject dataBlock, String fieldName, PointConversionInfo pointConversionInfo) {
        List<String> conversionStatus = pointConversionInfo.getConversionStatus();
        double propertyValue = -1;
        try {
            JsonElement fieldValue = dataBlock.get(fieldName);
            if (fieldValue == null || (fieldValue instanceof JsonNull) || fieldValue.getAsString().isEmpty()) {
                conversionStatus.add(String.format(CrsConversionServiceErrorMessages.MISSING_PROPERTY,fieldName));
                return propertyValue;
            }
            propertyValue = fieldValue.getAsDouble();
        } catch (ClassCastException ccEx) {
            conversionStatus.add(String.format(CrsConversionServiceErrorMessages.PROPERTY_VALUE_CAST_ERROR, fieldName, ccEx.getMessage()));
        } catch (NumberFormatException nfEx) {
            conversionStatus.add(String.format(CrsConversionServiceErrorMessages.ILLEGAL_PROPERTY_VALUE, fieldName, nfEx.getMessage()));
        } catch (IllegalStateException isEx) {
            conversionStatus.add(String.format(CrsConversionServiceErrorMessages.ILLEGAL_PROPERTY_VALUE, fieldName, isEx.getMessage()));
        } catch (Exception e) {
            conversionStatus.add(String.format(CrsConversionServiceErrorMessages.ILLEGAL_PROPERTY_VALUE, fieldName, e.getMessage()));
        }
        return propertyValue;
    }

    private String getCaseSensitivePropertyY(List<String> propertyNames, String lowerCaseName) {
        for (String name : propertyNames) {
            if (name.equalsIgnoreCase(lowerCaseName)) {
                return name;
            }
        }
        return null;
    }

    private PointConversionInfo crsConversionWithNestedPropertyNames(List<JsonObject> originalRecords, String persistableReference, JsonObject dataBlock, JsonArray metaPropertyNames, PointConversionInfo pointConversionInfo, List<JsonObject> metaBlocks) {
        Set<String> nestedPropertyNames = this.crsPropertySet.getNestedPropertyNames();
        List<String> conversionStatusForAMetaBlock = pointConversionInfo.getConversionStatus();
        String nestedFieldName= metaPropertyNames.get(0).getAsString();

        if (!nestedPropertyNames.contains(nestedFieldName)) {
            String errorMessage = String.format(CrsConversionServiceErrorMessages.INVALID_NESTED_PROPERTY_NAME,nestedFieldName);
            return this.addAndSetErrorInPointConversionInfo(conversionStatusForAMetaBlock, pointConversionInfo, errorMessage);
        }

        JsonElement nestedFieldValue = dataBlock.get(nestedFieldName);
        if (nestedFieldValue == null) {
            String errorMessage = String.format(CrsConversionServiceErrorMessages.MISSING_PROPERTY, nestedFieldName);
            return this.addAndSetErrorInPointConversionInfo(conversionStatusForAMetaBlock, pointConversionInfo, errorMessage);
        }

        try {
            JsonObject nestedProperty = nestedFieldValue.getAsJsonObject();
            JsonArray originalJsonPoints = nestedProperty.getAsJsonArray(POINTS);
            if (originalJsonPoints == null || originalJsonPoints.size() == 0) {
                conversionStatusForAMetaBlock.add(CrsConversionServiceErrorMessages.MISSING_POINTS_IN_NESTED_PROPERTY);
                pointConversionInfo.setConversionStatus(conversionStatusForAMetaBlock);
                pointConversionInfo.setHasError(true);
                return pointConversionInfo;
            }

            List<Point> originalPoints = new ArrayList<>();
            for (JsonElement jsonElementPoint : originalJsonPoints) {
                JsonArray jsonPoint = jsonElementPoint.getAsJsonArray();
                Point point = new Point();
                point.setX(jsonPoint.get(0).getAsDouble());
                point.setY(jsonPoint.get(1).getAsDouble());
                point.setZ(0.0);
                originalPoints.add(point);
            }

            ICrsConverterService crsConverterService = this.crsConverterFactory.create(this.customizeHeaderBeforeCallingCrsConversion(this.dpsHeaders));
            ConvertPointsRequest request = new ConvertPointsRequest(persistableReference, TO_CRS, originalPoints);

            ConvertPointsResponse response = crsConverterService.convertPoints(request);
            List<Point> convertedPoints = response.getPoints();

            JsonArray convertedJsonPoints = new JsonArray();
            for (int i = 0; i < convertedPoints.size(); i++ ) {
                Point convertedPoint = convertedPoints.get(i);
                JsonArray pointValues = new JsonArray();
                pointValues.add(convertedPoint.getX());
                pointValues.add(convertedPoint.getY());
                pointValues.add(convertedPoint.getZ());
                convertedJsonPoints.add(pointValues);
            }
            nestedProperty.remove(POINTS);
            nestedProperty.add(POINTS, convertedJsonPoints);
            dataBlock.add(nestedFieldName, nestedProperty);

            int metaItemIndex = pointConversionInfo.getMetaItemIndex();
            JsonObject metaItem = metaBlocks.get(metaItemIndex);
            metaItem.remove(PERSISTABLE_REFERENCE);
            metaItem.addProperty(PERSISTABLE_REFERENCE, TO_CRS);
            metaBlocks.set(metaItemIndex, metaItem);
            JsonArray metas = new JsonArray();
            for (JsonObject m : metaBlocks) {
                metas.add(m);
            }

            int recordIndex = pointConversionInfo.getRecordIndex();
            JsonObject originalRecord = originalRecords.get(recordIndex);
            originalRecord.add(DATA, dataBlock);
            originalRecord.add(META, metas);
            originalRecords.set(recordIndex, originalRecord);
            return pointConversionInfo;
        } catch (CrsConverterException cvEx) {
            if (cvEx.getHttpResponse().IsBadRequestCode()) {
                conversionStatusForAMetaBlock.add(CrsConversionServiceErrorMessages.BAD_REQUEST_FROM_CRS);
            } else {
                this.logger.error(String.format(CrsConversionServiceErrorMessages.CRS_OTHER_ERROR, cvEx.getHttpResponse().toString()));
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, "crs conversion service error.");
            }
        } catch (ClassCastException ccEx) {
            conversionStatusForAMetaBlock.add(String.format(CrsConversionServiceErrorMessages.ILLEGAL_DATA_IN_NESTED_PROPERTY, nestedFieldName, ccEx.getMessage()));
        } catch (IllegalStateException isEx) {
            conversionStatusForAMetaBlock.add(String.format(CrsConversionServiceErrorMessages.ILLEGAL_DATA_IN_NESTED_PROPERTY, nestedFieldName, isEx.getMessage()));
        } catch (Exception e) {
            conversionStatusForAMetaBlock.add(e.getMessage());
        }

        if (conversionStatusForAMetaBlock.size() != 0) {
            pointConversionInfo.setConversionStatus(conversionStatusForAMetaBlock);
            pointConversionInfo.setHasError(true);
        }
        return pointConversionInfo;
    }

    private PointConversionInfo addAndSetErrorInPointConversionInfo(List<String> conversionStatusForAMetaBlock, PointConversionInfo pointConversionInfo, String errorMessage) {
        conversionStatusForAMetaBlock.add(errorMessage);
        pointConversionInfo.setConversionStatus(conversionStatusForAMetaBlock);
        pointConversionInfo.setHasError(true);
        return pointConversionInfo;
    }

    private void addPointConversionInfoIntoConversionMap(String reference, PointConversionInfo pointInfo, Map<String, List<PointConversionInfo>> pointsToBeConverted) {
        if (pointsToBeConverted == null) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, "points to be converted map is null");
        }

        List<PointConversionInfo> listOfPointsWithSameReference = pointsToBeConverted.get(reference);
        if (listOfPointsWithSameReference == null) {
            listOfPointsWithSameReference = new ArrayList<>();
        }

        listOfPointsWithSameReference.add(pointInfo);
        pointsToBeConverted.put(reference, listOfPointsWithSameReference);
    }


    List<PointConversionInfo> callClientLibraryDoConversion(Map<String, List<PointConversionInfo>> originalPointsMap, List<ConversionStatus> conversionStatuses) {
        ICrsConverterService crsConverterService = this.crsConverterFactory.create(this.customizeHeaderBeforeCallingCrsConversion(this.dpsHeaders));
        List<PointConversionInfo> convertedPointInfo = new ArrayList<>();

        for (Map.Entry<String, List<PointConversionInfo>> entry : originalPointsMap.entrySet()) {
            List<Point> pointsToBeConverted = new ArrayList<>();
            List<PointConversionInfo> pointsList = entry.getValue();

            for (PointConversionInfo point : pointsList) {
                Point toBeConverted = this.constructPointFromPointConversionInfo(point);
                pointsToBeConverted.add(toBeConverted);
            }

            ConvertPointsRequest request = new ConvertPointsRequest(entry.getKey(), TO_CRS, pointsToBeConverted);
            try {
                ConvertPointsResponse response = crsConverterService.convertPoints(request);
                List<Point> convertedPoints = response.getPoints();

                convertedPointInfo.addAll(this.putBackConvertedValueIntoPointsInfo(pointsList, convertedPoints, conversionStatuses));
            } catch (CrsConverterException e) {
                if (e.getHttpResponse().IsBadRequestCode()) {
                    convertedPointInfo.addAll(this.putDataErrorFromCrsIntoPointsInfo(pointsList));
                    continue;
                } else {
                    this.logger.error(String.format(CrsConversionServiceErrorMessages.CRS_OTHER_ERROR, e.getHttpResponse().toString()));
                    throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, "crs conversion service error.");
                }
            }
        }
        return convertedPointInfo;
    }

    private Point constructPointFromPointConversionInfo(PointConversionInfo pointConversionInfo) {
        Point point = new Point();
        point.setX(pointConversionInfo.getXValue());
        point.setY(pointConversionInfo.getYValue());
        point.setZ(pointConversionInfo.getZValue());
        return point;
    }

    private List<PointConversionInfo> putBackConvertedValueIntoPointsInfo(List<PointConversionInfo> convertedPointInfo, List<Point> convertedPoints, List<ConversionStatus> conversionStatuses) {
        for (int i = 0; i < convertedPointInfo.size(); i++) {
            Point point = convertedPoints.get(i);
            PointConversionInfo toBeUpdatedInfo = convertedPointInfo.get(i);
            List<String> status = toBeUpdatedInfo.getConversionStatus();
            if (status == null) {
                status = new ArrayList<>();
            }

            if (point == null) {
                status.add(CONVERSION_FAILURE);
                toBeUpdatedInfo.setConversionStatus(status);
                toBeUpdatedInfo.setHasError(true);
                this.addConversionInfoToStatuses(status, toBeUpdatedInfo.getRecordId(), conversionStatuses, true);
                continue;
            }
            toBeUpdatedInfo.setXValue(point.getX());
            toBeUpdatedInfo.setYValue(point.getY());
            toBeUpdatedInfo.setZValue(point.getZ());

            int metaItemIndex = toBeUpdatedInfo.getMetaItemIndex();
            List<JsonObject> metaBlocks = toBeUpdatedInfo.getMetaItems();
            JsonObject metaItem = metaBlocks.get(metaItemIndex);
            metaItem.remove(PERSISTABLE_REFERENCE);
            metaItem.addProperty(PERSISTABLE_REFERENCE, TO_CRS);
            metaBlocks.set(metaItemIndex, metaItem);
            toBeUpdatedInfo.setMetaItems(metaBlocks);
        }
        return convertedPointInfo;
    }

    private List<PointConversionInfo> putDataErrorFromCrsIntoPointsInfo(List<PointConversionInfo> convertedPointInfo) {
        for (int i = 0; i < convertedPointInfo.size(); i++) {
            PointConversionInfo toBeUpdatedInfo = convertedPointInfo.get(i);
            List<String> status = toBeUpdatedInfo.getConversionStatus();
            if (status == null) {
                status = new ArrayList<>();
            }

            status.add(CrsConversionServiceErrorMessages.BAD_REQUEST_FROM_CRS);
            toBeUpdatedInfo.setConversionStatus(status);
            toBeUpdatedInfo.setHasError(true);
        }
        return convertedPointInfo;
    }

    private void updateValuesInRecord(JsonObject recordJsonObject, PointConversionInfo convertedInfo, List<ConversionStatus> conversionStatuses) {
        JsonObject dataBlcok = recordJsonObject.getAsJsonObject(DATA);

        dataBlcok.remove(convertedInfo.getXFieldName());
        dataBlcok.remove(convertedInfo.getYFieldName());
        dataBlcok.remove(convertedInfo.getZFieldName());

        dataBlcok.addProperty(convertedInfo.getXFieldName(), convertedInfo.getXValue());
        dataBlcok.addProperty(convertedInfo.getYFieldName(), convertedInfo.getYValue());
        dataBlcok.addProperty(convertedInfo.getZFieldName(), convertedInfo.getZValue());

        recordJsonObject.add(DATA, dataBlcok);

        List<JsonObject> metaBlocks = convertedInfo.getMetaItems();
        JsonArray metas = new JsonArray();
        for (JsonObject m : metaBlocks) {
            metas.add(m);
        }
        recordJsonObject.add(META, metas);

        this.addConversionInfoToStatuses(convertedInfo.getConversionStatus(), this.getRecordId(recordJsonObject), conversionStatuses, convertedInfo.isHasError());
    }

    private void addConversionInfoToStatuses(List<String> conversionInfo, String recordId, List<ConversionStatus> conversionStatuses, boolean hasEror) {
        if (conversionStatuses== null) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, "statuses is null");
        }

        int conversionStatusIndex = this.getConversionStatusIndexFromList(conversionStatuses, recordId);
        ConversionStatus conversionStatus;
        if (conversionStatusIndex == -1) {
            conversionStatus = new ConversionStatus();
            conversionStatus.setId(recordId);
        } else {
            conversionStatus = conversionStatuses.get(conversionStatusIndex);
            List<String> errors = conversionStatus.getErrors();
            conversionInfo.addAll(errors);
        }
        conversionStatus.setErrors(conversionInfo);
        if (hasEror) {
            conversionStatus.setStatus(ConvertStatus.ERROR.toString());
        }
        if (conversionStatusIndex == -1) {
            conversionStatuses.add(conversionStatus);
        } else {
            conversionStatuses.set(conversionStatusIndex, conversionStatus);
        }
    }

    private int getConversionStatusIndexFromList(List<ConversionStatus> conversionStatuses, String recordId) {
        for (int i = 0; i < conversionStatuses.size(); i++) {
            if (conversionStatuses.get(i).getId().equalsIgnoreCase(recordId)) {
                return i;
            }
        }
        return -1;
    }

    private String getRecordId(JsonObject record) {
        JsonElement recordId = record.get("id");
        if (recordId == null || recordId instanceof JsonNull || recordId.getAsString().isEmpty()) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, "record does not have id");
        }
        return recordId.getAsString();
    }

    private DpsHeaders customizeHeaderBeforeCallingCrsConversion(DpsHeaders dpsHeaders) {
        String token = this.jwtClient.getIdToken(dpsHeaders.getPartitionId());
        if (Strings.isNullOrEmpty(token)) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, "authorization for crs conversion failed");
        }
        DpsHeaders headers = DpsHeaders.createFromMap(dpsHeaders.getHeaders());
        headers.put(DpsHeaders.AUTHORIZATION, token);
        headers.put(DpsHeaders.DATA_PARTITION_ID, dpsHeaders.getPartitionId());
        return headers;
    }
}
