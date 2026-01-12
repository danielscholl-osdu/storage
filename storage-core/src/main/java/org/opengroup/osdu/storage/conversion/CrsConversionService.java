// Copyright 2017-2023, Schlumberger
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.crs.CrsConversionServiceErrorMessages;
import org.opengroup.osdu.core.common.crs.ICrsConverterFactory;
import org.opengroup.osdu.core.common.crs.ICrsConverterService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.crs.ConvertGeoJsonRequest;
import org.opengroup.osdu.core.common.model.crs.ConvertGeoJsonResponse;
import org.opengroup.osdu.core.common.model.crs.ConvertPointsRequest;
import org.opengroup.osdu.core.common.model.crs.ConvertPointsResponse;
import org.opengroup.osdu.core.common.model.crs.ConvertStatus;
import org.opengroup.osdu.core.common.model.crs.CrsConverterException;
import org.opengroup.osdu.core.common.model.crs.CrsPropertySet;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonBase;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonFeature;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonFeatureCollection;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonGeometryCollection;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonLineString;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonMultiLineString;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonMultiPoint;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonMultiPolygon;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonPoint;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonPolygon;
import org.opengroup.osdu.core.common.model.crs.Point;
import org.opengroup.osdu.core.common.model.crs.PointConversionInfo;
import org.opengroup.osdu.core.common.model.crs.RecordsAndStatuses;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.RequestStatus;
import org.opengroup.osdu.core.common.model.storage.ConversionStatus;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.storage.di.CrsConversionConfig;
import org.opengroup.osdu.storage.di.SpringConfig;
import org.opengroup.osdu.storage.util.ConversionJsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.opengroup.osdu.core.common.Constants.*;
import static org.opengroup.osdu.core.common.crs.CrsConversionServiceErrorMessages.MISSING_GEOMETRIES;
import static org.opengroup.osdu.core.common.util.JsonUtils.jsonElementToString;

@Service
public class CrsConversionService {
    private static final String TO_CRS = "{\"wkt\":\"GEOGCS[\\\"GCS_WGS_1984\\\",DATUM[\\\"D_WGS_1984\\\",SPHEROID[\\\"WGS_1984\\\",6378137.0,298.257223563]],PRIMEM[\\\"Greenwich\\\",0.0],UNIT[\\\"Degree\\\",0.0174532925199433],AUTHORITY[\\\"EPSG\\\",4326]]\",\"ver\":\"PE_10_3_1\",\"name\":\"GCS_WGS_1984\",\"authCode\":{\"auth\":\"EPSG\",\"code\":\"4326\"},\"type\":\"LBC\"}";
    private static final String TO_CRS_GEO_JSON = "{\"authCode\":{\"auth\":\"EPSG\",\"code\":\"4326\"},\"name\":\"GCS_WGS_1984\",\"type\":\"LBC\",\"ver\":\"PE_10_3_1\",\"wkt\":\"GEOGCS[\\\"GCS_WGS_1984\\\",DATUM[\\\"D_WGS_1984\\\",SPHEROID[\\\"WGS_1984\\\",6378137.0,298.257223563]],PRIMEM[\\\"Greenwich\\\",0.0],UNIT[\\\"Degree\\\",0.0174532925199433],AUTHORITY[\\\"EPSG\\\",4326]]\"}";
    private static final String TO_UNIT_Z = "{\"baseMeasurement\":{\"ancestry\":\"Length\",\"type\":\"UM\"},\"scaleOffset\":{\"offset\":0.0,\"scale\":1.0},\"symbol\":\"m\",\"type\":\"USO\"}";
    private static final String UNKNOWN_ERROR = "unknown error";
    private static final String INVALID_COORDINATES = "CRS conversion: invalid Coordinates values, no conversion applied. Error: %s";
    private static final String BAD_REQUEST = "CRS conversion: bad request from crs converter, no conversion applied. Response From CRS Converter: %s.";
    private static final String TIMEOUT_FAILURE = "CRS conversion: timeout on crs converter request, no conversion applied. Affected property: %s. Response From CRS Converter: %s.";
    private static final String CONVERSION_FAILURE = "CRS Conversion: point conversion failure (null response from crs converter), no conversion applied. Affected property names: %s, %s";
    private static final String OTHER_FAILURE = "CRS conversion: error from crs converter, no conversion applied. Affected property: %s. Response from CRS converter: %s.";

    @Autowired
    private CrsPropertySet crsPropertySet;

    @Autowired
    @Lazy
    private DpsConversionService dpsConversionService;

    @Autowired
    private ICrsConverterFactory crsConverterFactory;

    @Autowired
    private DpsHeaders dpsHeaders;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private IServiceAccountJwtClient jwtClient;
    
    @Autowired
    private SpringConfig springConfig;

    @Autowired
    private ConversionJsonUtils conversionJsonUtils;

    @Autowired
    private CrsConversionConfig crsConversionConfig;

    public RecordsAndStatuses doCrsConversion(List<JsonObject> originalRecords, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses) {
        RecordsAndStatuses crsConversionResult = new RecordsAndStatuses();
        Map<String, List<PointConversionInfo>> pointConversionInfoList = this.gatherCrsConversionData(originalRecords, conversionStatuses);

        if (pointConversionInfoList.isEmpty()) {
            crsConversionResult.setRecords(originalRecords);
            crsConversionResult.setConversionStatuses(this.buildConversionStatuses(conversionStatuses));
            return crsConversionResult;
        }

        List<PointConversionInfo> convertedPointsInfo = this.callClientLibraryDoConversion(pointConversionInfoList, conversionStatuses);
        for (PointConversionInfo convertedInfo: convertedPointsInfo) {
            JsonObject record = originalRecords.get(convertedInfo.getRecordIndex());
            this.updateValuesInRecord(record, convertedInfo, conversionStatuses);
            originalRecords.set(convertedInfo.getRecordIndex(), record);
        }
        crsConversionResult.setConversionStatuses(this.buildConversionStatuses(conversionStatuses));
        crsConversionResult.setRecords(originalRecords);
        return crsConversionResult;
    }

    public RecordsAndStatuses doCrsGeoJsonConversion(List<JsonObject> originalRecords, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses) {
        RecordsAndStatuses crsGeoJsonConversionResult = new RecordsAndStatuses();
        this.gatherCrsGeoJsonConversionData(originalRecords, conversionStatuses);
        crsGeoJsonConversionResult.setConversionStatuses(this.buildConversionStatuses(conversionStatuses));
        crsGeoJsonConversionResult.setRecords(originalRecords);
        return crsGeoJsonConversionResult;
    }

    private List<ConversionStatus> buildConversionStatuses(List<ConversionStatus.ConversionStatusBuilder> builders) {
        List<ConversionStatus> result = new ArrayList<>();
        for (ConversionStatus.ConversionStatusBuilder builder : builders) {
            result.add(builder.build());
        }
        return result;
    }

    private Map<String, List<PointConversionInfo>> gatherCrsConversionData(List<JsonObject> originalRecords, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses) {
        Map<String, List<PointConversionInfo>> batchPointConversionMap = new HashMap<>();

        for (int i = 0; i < originalRecords.size(); i++) {
            JsonObject recordJsonObject = originalRecords.get(i);
            String recordId = this.getRecordId(recordJsonObject);
            ConversionStatus.ConversionStatusBuilder statusBuilder = this.getConversionStatusBuilderFromList(recordId, conversionStatuses);
            if(statusBuilder == null){
                continue;
            }
            JsonObject dataBlock = recordJsonObject.getAsJsonObject(Constants.DATA);
            if (dataBlock == null) {
                statusBuilder.addError(CrsConversionServiceErrorMessages.MISSING_DATA_BLOCK);
                continue;
            }

            List<JsonObject> metaBlocks = this.extractValidMetaItemsFromRecord(recordJsonObject, statusBuilder);
            for (int j = 0; j < metaBlocks.size(); j++) {
                JsonObject metaBlock = metaBlocks.get(j);
                if (!metaBlock.get(Constants.KIND).getAsString().equalsIgnoreCase(Constants.CRS)) {
                    continue;
                }
                this.constructPointConversionInfoList(originalRecords, recordId, metaBlock, i, batchPointConversionMap, dataBlock, j, metaBlocks, statusBuilder);
            }
        }
        return batchPointConversionMap;
    }

    private void gatherCrsGeoJsonConversionData(List<JsonObject> originalRecords, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses) {
        for (JsonObject recordJsonObject : originalRecords) {
            String recordId = this.getRecordId(recordJsonObject);
            ConversionStatus.ConversionStatusBuilder statusBuilder = this.getConversionStatusBuilderFromList(recordId, conversionStatuses);
            if(statusBuilder == null){
                continue;
            }
            List<String> validationErrors = new ArrayList<>();
            JsonObject filteredObjects = this.dpsConversionService.filterDataFields(recordJsonObject, validationErrors);
            for (String attributeName : filteredObjects.keySet()) {
                JsonObject asIngestedCoordinates = filteredObjects.getAsJsonObject(attributeName).getAsJsonObject(AS_INGESTED_COORDINATES);
                if (asIngestedCoordinates != null) {
                    GeoJsonFeatureCollection fc = new GeoJsonFeatureCollection();
                    if (conversionJsonUtils.isJsonObjectContainsProperty(asIngestedCoordinates, TYPE)) fc.setType(asIngestedCoordinates.get(TYPE).getAsString());
                    if (conversionJsonUtils.isJsonObjectContainsProperty(asIngestedCoordinates, PROPERTIES) && conversionJsonUtils.isPropertyJsonObject(asIngestedCoordinates, PROPERTIES, statusBuilder)) fc.setProperties(asIngestedCoordinates.getAsJsonObject(PROPERTIES));
                    if (conversionJsonUtils.isJsonObjectContainsProperty(asIngestedCoordinates, PERSISTABLE_REFERENCE_CRS)) fc.setPersistableReferenceCrs(asIngestedCoordinates.get(PERSISTABLE_REFERENCE_CRS).getAsString());
                    if (conversionJsonUtils.isJsonObjectContainsProperty(asIngestedCoordinates, COORDINATE_REFERENCE_SYSTEM_ID)) fc.setCoordinateReferenceSystemID(asIngestedCoordinates.get(COORDINATE_REFERENCE_SYSTEM_ID).getAsString());
                    if (conversionJsonUtils.isJsonObjectContainsProperty(asIngestedCoordinates, VERTICAL_UNIT_ID)) fc.setVerticalUnitID(asIngestedCoordinates.get(VERTICAL_UNIT_ID).getAsString());
                    if (conversionJsonUtils.isJsonObjectContainsProperty(asIngestedCoordinates, PERSISTABLE_REFERENCE_UNIT_Z)) fc.setPersistableReferenceUnitZ(asIngestedCoordinates.get(PERSISTABLE_REFERENCE_UNIT_Z).getAsString());
                    if (conversionJsonUtils.isJsonObjectContainsProperty(asIngestedCoordinates, BBOX) && conversionJsonUtils.isPropertyJsonArray(asIngestedCoordinates, BBOX, statusBuilder)) fc.setBbox(this.bboxValues(asIngestedCoordinates.getAsJsonArray(BBOX)));

                    JsonArray featuresArray = conversionJsonUtils.isJsonObjectContainsProperty(asIngestedCoordinates, FEATURES) && conversionJsonUtils.isPropertyJsonArray(asIngestedCoordinates, FEATURES, statusBuilder) ? asIngestedCoordinates.getAsJsonArray(FEATURES) : null;
                    if (featuresArray == null || featuresArray.size() == 0) {
                        statusBuilder.addError(CrsConversionServiceErrorMessages.MISSING_FEATURES);
                        continue;
                    }
                    GeoJsonFeature[] featureArray = new GeoJsonFeature[featuresArray.size()];
                    for (int j = 0; j < featuresArray.size(); j++) {
                        JsonObject featureItem = featuresArray.get(j).getAsJsonObject();
                        featureArray[j] = this.getFeature(featureItem, statusBuilder);
                    }
                    fc.setFeatures(featureArray);

                    ICrsConverterService crsConverterService = this.crsConverterFactory.create(this.customizeHeaderBeforeCallingCrsConversion(this.dpsHeaders), getRequestConfig());
                    ConvertGeoJsonRequest request = new ConvertGeoJsonRequest(fc, TO_CRS_GEO_JSON, TO_UNIT_Z);
                    try {
                        if (statusBuilder.getErrors().isEmpty()) {
                            ConvertGeoJsonResponse response = crsConverterService.convertGeoJson(request);
                            GeoJsonFeatureCollection wgs84Coordinates = response.getFeatureCollection();
                            wgs84Coordinates.setCoordinateReferenceSystemID(null);
                            wgs84Coordinates.setVerticalUnitID(null);
                            this.appendObjectInRecord(recordJsonObject, attributeName, wgs84Coordinates);
                        }
                    } catch (CrsConverterException crsEx) {
                        if (crsEx.getHttpResponse().IsBadRequestCode()) {
                            statusBuilder.addError(String.format(BAD_REQUEST, crsEx.getHttpResponse().getBody()));
                        } else if (crsEx.getHttpResponse().getResponseCode() == HttpStatus.SC_GATEWAY_TIMEOUT) {
                            statusBuilder.addError(String.format(TIMEOUT_FAILURE, attributeName, crsEx.getHttpResponse().getBody()));
                        } else {
                            String message = String.format(OTHER_FAILURE, attributeName, crsEx.getHttpResponse().toString());
                            this.logger.error(message);
                            statusBuilder.addError(message);
                        }
                    } catch (AppException ex) {
                        statusBuilder.addError(String.format(OTHER_FAILURE, attributeName, ex.getError().getMessage()));
                    }
                } else {
                    statusBuilder.addError(CrsConversionServiceErrorMessages.MISSING_AS_INGESTED_COORDINATES);
                }
            }
        }
    }

    private List<JsonObject> extractValidMetaItemsFromRecord(JsonObject recordJsonObject, ConversionStatus.ConversionStatusBuilder conversionStatusBuilder) {
        try {
            JsonArray metaItemsArray = recordJsonObject.getAsJsonArray(Constants.META);
            for (int i = 0; i < metaItemsArray.size(); i++) {
                JsonObject metaItem = metaItemsArray.get(i).getAsJsonObject();
                conversionStatusBuilder.addErrorsFromMetaItemChecking(metaItem);
            }
        } catch (Exception e) {
            conversionStatusBuilder.addError(String.format(CrsConversionServiceErrorMessages.ILLEGAL_METAITEM_ARRAY, e.getMessage()));
        }
        return conversionStatusBuilder.getValidMetaItems();
    }

    private List<PointConversionInfo> constructPointConversionInfoList(List<JsonObject> originalRecords, String recordId, JsonObject metaItem, int recordIndex, Map<String, List<PointConversionInfo>> mapOfPoints, JsonObject dataBlock, int metaItemIndex, List<JsonObject> metaBlocks, ConversionStatus.ConversionStatusBuilder conversionStatusBuilder) {
        List<PointConversionInfo> pointConversionInfoList = new ArrayList<>();
        String persistableReference = jsonElementToString(metaItem.get(Constants.PERSISTABLE_REFERENCE));
        JsonArray propertyNamesArray = metaItem.get(Constants.PROPERTY_NAMES).getAsJsonArray();
        List<String> propertyNames = this.convertPropertyNamesToStringList(propertyNamesArray);
        List<String> propertyNamesRemain = new ArrayList<>();
        for (String name: propertyNames) {
            propertyNamesRemain.add(name.toLowerCase());
        }
        int propertySize = propertyNames.size();

        // nested property with point list
        if (propertySize == 1) {
            PointConversionInfo pointConversionInfo = this.initializePoint(recordIndex, recordId, metaItemIndex, metaBlocks, conversionStatusBuilder);
            pointConversionInfoList.add(this.crsConversionWithNestedPropertyNames(originalRecords, persistableReference, dataBlock, propertyNamesArray, pointConversionInfo, metaBlocks));
            return pointConversionInfoList;
        }

        Map<String, String> propertyPairingMap = this.crsPropertySet.getPropertyPairing();
        for (int i = 0; i < propertyNames.size(); i++) {
            String propertyX = propertyNames.get(i);

            String[] lowerCasePropertyXs = propertyX.toLowerCase().split("\\.");
            int propertyXsLength = lowerCasePropertyXs.length;

            String lowerCaseInnerX = lowerCasePropertyXs[propertyXsLength - 1];

            if (propertyPairingMap.get(lowerCaseInnerX) == null) {
                // either an y property or an unsupported property
                continue;
            } else {
                // find a pair of x,y
                String innerY = propertyPairingMap.get(lowerCaseInnerX);
                // if x is nested, then paired y should share the same outer structure
                StringBuilder propertyYBuilder = new StringBuilder();
                for (int j = 0; j < propertyXsLength - 1; j++) {
                    propertyYBuilder.append(lowerCasePropertyXs[j]);
                    propertyYBuilder.append(".");
                }
                propertyYBuilder.append(innerY);
                String propertyY = propertyYBuilder.toString();
                if (propertyNamesRemain.contains(propertyY)) {
                    propertyY = this.getCaseSensitivePropertyY(propertyNames, propertyY);
                    PointConversionInfo pointConversionInfo = this.initializePoint(recordIndex, recordId, metaItemIndex, metaBlocks, conversionStatusBuilder);

                    pointConversionInfo.setXFieldName(propertyX);
                    pointConversionInfo.setYFieldName(propertyY);
                    pointConversionInfo.setXValue(this.extractPropertyFromDataBlock(dataBlock, propertyX, conversionStatusBuilder));
                    pointConversionInfo.setYValue(this.extractPropertyFromDataBlock(dataBlock, propertyY, conversionStatusBuilder));
                    pointConversionInfo.setZFieldName("Z");
                    pointConversionInfo.setZValue(0.0);
                    pointConversionInfoList.add(pointConversionInfo);

                    if (conversionStatusBuilder.getStatus().equalsIgnoreCase(ConvertStatus.SUCCESS.toString())) {
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
            for (String name : propertyNamesRemain) {
                conversionStatusBuilder.addMessage(String.format(CrsConversionServiceErrorMessages.PAIR_FAILURE, name));
            }
        }
        return pointConversionInfoList;
    }

    private PointConversionInfo initializePoint(int recordIndex, String recordId, int metaItemIndex, List<JsonObject> metaBlocks, ConversionStatus.ConversionStatusBuilder conversionStatusBuilder) {
        PointConversionInfo pointConversionInfo = new PointConversionInfo();
        pointConversionInfo.setStatusBuilder(conversionStatusBuilder);
        pointConversionInfo.setRecordIndex(recordIndex);
        pointConversionInfo.setRecordId(recordId);
        pointConversionInfo.setMetaItemIndex(metaItemIndex);
        pointConversionInfo.setMetaItems(metaBlocks);

        return pointConversionInfo;
    }

    private List<String> convertPropertyNamesToStringList(JsonArray propertyNamesJsonArray) {
        List<String> propertyNames = new ArrayList<>();
        for (JsonElement p : propertyNamesJsonArray) {
            propertyNames.add(p.getAsString());
        }
        return propertyNames;
    }

    private double extractPropertyFromDataBlock(JsonObject dataBlock, String fieldName, ConversionStatus.ConversionStatusBuilder conversionStatusBuilder) {
        double propertyValue = -1;
        try {
            String[] nestedNames = fieldName.split("\\.");
            JsonObject outer = dataBlock;
            JsonObject inner = dataBlock;

            // This loop is to help get nested properties from data block, outer would be datablock itself, and get updated to next level each turn.
            for (int i = 0; i < nestedNames.length - 1; i++) {
                inner = outer.getAsJsonObject(nestedNames[i]);
                outer = inner;
            }
            // get the very last nested property value, e.g, x.y.z, it should return the value of z
            JsonElement fieldValue = inner.get(nestedNames[nestedNames.length - 1]);

            if (fieldValue == null || (fieldValue instanceof JsonNull) || fieldValue.getAsString().isEmpty()) {
                conversionStatusBuilder.addError(String.format(CrsConversionServiceErrorMessages.MISSING_PROPERTY,fieldName));
                return propertyValue;
            }
            propertyValue = fieldValue.getAsDouble();

        } catch (ClassCastException ccEx) {
            conversionStatusBuilder.addError(String.format(CrsConversionServiceErrorMessages.PROPERTY_VALUE_CAST_ERROR, fieldName, ccEx.getMessage()));
        } catch (NumberFormatException nfEx) {
            conversionStatusBuilder.addError(String.format(CrsConversionServiceErrorMessages.ILLEGAL_PROPERTY_VALUE, fieldName, nfEx.getMessage()));
        } catch (IllegalStateException isEx) {
            conversionStatusBuilder.addError(String.format(CrsConversionServiceErrorMessages.ILLEGAL_PROPERTY_VALUE, fieldName, isEx.getMessage()));
        } catch (Exception e) {
            conversionStatusBuilder.addError(String.format(CrsConversionServiceErrorMessages.ILLEGAL_PROPERTY_VALUE, fieldName, e.getMessage()));
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
        ConversionStatus.ConversionStatusBuilder statusBuilder = pointConversionInfo.getStatusBuilder();
        String nestedFieldName= metaPropertyNames.get(0).getAsString();

        if (!nestedPropertyNames.contains(nestedFieldName)) {
            String errorMessage = String.format(CrsConversionServiceErrorMessages.INVALID_NESTED_PROPERTY_NAME,nestedFieldName);
            statusBuilder.addError(errorMessage);
            return pointConversionInfo;
        }

        JsonElement nestedFieldValue = dataBlock.get(nestedFieldName);
        if (nestedFieldValue == null) {
            String errorMessage = String.format(CrsConversionServiceErrorMessages.MISSING_PROPERTY, nestedFieldName);
            statusBuilder.addError(errorMessage);
            return pointConversionInfo;
        }

        try {
            JsonObject nestedProperty = nestedFieldValue.getAsJsonObject();
            JsonArray originalJsonPoints = nestedProperty.getAsJsonArray(Constants.POINTS);
            if (originalJsonPoints == null || originalJsonPoints.size() == 0) {
                statusBuilder.addError(CrsConversionServiceErrorMessages.MISSING_POINTS_IN_NESTED_PROPERTY);
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

            ICrsConverterService crsConverterService = this.crsConverterFactory.create(this.customizeHeaderBeforeCallingCrsConversion(this.dpsHeaders), getRequestConfig());
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
            nestedProperty.remove(Constants.POINTS);
            nestedProperty.add(Constants.POINTS, convertedJsonPoints);
            dataBlock.add(nestedFieldName, nestedProperty);

            int metaItemIndex = pointConversionInfo.getMetaItemIndex();
            JsonObject metaItem = metaBlocks.get(metaItemIndex);
            metaItem.remove(Constants.PERSISTABLE_REFERENCE);
            metaItem.addProperty(Constants.PERSISTABLE_REFERENCE, TO_CRS);
            metaBlocks.set(metaItemIndex, metaItem);
            JsonArray metas = new JsonArray();
            for (JsonObject m : metaBlocks) {
                metas.add(m);
            }

            int recordIndex = pointConversionInfo.getRecordIndex();
            JsonObject originalRecord = originalRecords.get(recordIndex);
            originalRecord.add(Constants.DATA, dataBlock);
            originalRecord.add(Constants.META, metas);
            originalRecords.set(recordIndex, originalRecord);
            return pointConversionInfo;
        } catch (CrsConverterException cvEx) {
            if (cvEx.getHttpResponse().IsBadRequestCode()) {
                statusBuilder.addError(String.format(CrsConversionServiceErrorMessages.BAD_REQUEST_FROM_CRS, cvEx.getHttpResponse().getBody(), nestedFieldName));
            } else {
                this.logger.error(String.format(OTHER_FAILURE, nestedFieldName, cvEx.getHttpResponse().toString()));
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, "crs conversion service error.");
            }
        } catch (ClassCastException | IllegalStateException ccEx) {
            statusBuilder.addError(String.format(CrsConversionServiceErrorMessages.ILLEGAL_DATA_IN_NESTED_PROPERTY, nestedFieldName, ccEx.getMessage()));
        } catch (AppException ex) {
            statusBuilder.addError(String.format(OTHER_FAILURE, nestedFieldName, ex.getError().getMessage()));
        } catch (Exception e) {
            statusBuilder.addError(e.getMessage());
        }
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

    List<PointConversionInfo> callClientLibraryDoConversion(Map<String, List<PointConversionInfo>> originalPointsMap, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses) {
        ICrsConverterService crsConverterService = this.crsConverterFactory.create(this.customizeHeaderBeforeCallingCrsConversion(this.dpsHeaders), getRequestConfig());
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
                    convertedPointInfo.addAll(this.putDataErrorFromCrsIntoPointsInfo(pointsList, e.getMessage()));
                } else {
                    this.logger.error(String.format(CrsConversionServiceErrorMessages.CRS_OTHER_ERROR, e.getHttpResponse().toString()));
                    throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, "crs conversion service error.");
                }
            } catch (AppException ex) {
                convertedPointInfo.addAll(this.putDataErrorFromCrsIntoPointsInfo(pointsList, ex.getMessage()));
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

    private List<PointConversionInfo> putBackConvertedValueIntoPointsInfo(List<PointConversionInfo> convertedPointInfo, List<Point> convertedPoints, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses) {
        for (int i = 0; i < convertedPointInfo.size(); i++) {
            Point point = convertedPoints.get(i);
            PointConversionInfo toBeUpdatedInfo = convertedPointInfo.get(i);
            ConversionStatus.ConversionStatusBuilder statusBuilder = toBeUpdatedInfo.getStatusBuilder();

            if (point == null) {
                statusBuilder.addError(String.format(CONVERSION_FAILURE, toBeUpdatedInfo.getXFieldName(), toBeUpdatedInfo.getYFieldName()));
                continue;
            }
            toBeUpdatedInfo.setXValue(point.getX());
            toBeUpdatedInfo.setYValue(point.getY());
            toBeUpdatedInfo.setZValue(point.getZ());

            int metaItemIndex = toBeUpdatedInfo.getMetaItemIndex();
            List<JsonObject> metaBlocks = toBeUpdatedInfo.getMetaItems();
            JsonObject metaItem = metaBlocks.get(metaItemIndex);
            metaItem.remove(Constants.PERSISTABLE_REFERENCE);
            metaItem.addProperty(Constants.PERSISTABLE_REFERENCE, TO_CRS);
            metaBlocks.set(metaItemIndex, metaItem);
            toBeUpdatedInfo.setMetaItems(metaBlocks);
        }
        return convertedPointInfo;
    }

    private List<PointConversionInfo> putDataErrorFromCrsIntoPointsInfo(List<PointConversionInfo> convertedPointInfo, String errMsg) {
        for (int i = 0; i < convertedPointInfo.size(); i++) {
            PointConversionInfo toBeUpdatedInfo = convertedPointInfo.get(i);
            ConversionStatus.ConversionStatusBuilder statusBuilder = toBeUpdatedInfo.getStatusBuilder();

            statusBuilder.addCRSBadRequestError(errMsg, toBeUpdatedInfo.getXFieldName(), toBeUpdatedInfo.getYFieldName());
        }
        return convertedPointInfo;
    }

    private void updateValuesInRecord(JsonObject recordJsonObject, PointConversionInfo convertedInfo, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses) {
        JsonObject dataBlcok = recordJsonObject.getAsJsonObject(Constants.DATA);

        this.overwritePropertyToData(convertedInfo.getXFieldName(), convertedInfo.getXValue(), dataBlcok);
        this.overwritePropertyToData(convertedInfo.getYFieldName(), convertedInfo.getYValue(), dataBlcok);
        this.overwritePropertyToData(convertedInfo.getZFieldName(), convertedInfo.getZValue(), dataBlcok);

        recordJsonObject.add(Constants.DATA, dataBlcok);

        List<JsonObject> metaBlocks = convertedInfo.getMetaItems();
        JsonArray metas = new JsonArray();
        for (JsonObject m : metaBlocks) {
            metas.add(m);
        }
        recordJsonObject.add(Constants.META, metas);
    }

    private void appendObjectInRecord(JsonObject recordJsonObject, String attributeName, GeoJsonFeatureCollection wgs84Coordinates) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            String jsonString = mapper.writeValueAsString(wgs84Coordinates);
            JsonParser parser = new JsonParser();
            JsonObject convertObj = (JsonObject) parser.parse(jsonString);

            JsonObject dataBlock = recordJsonObject.getAsJsonObject(Constants.DATA);
            JsonObject conversionBlock = recordJsonObject.getAsJsonObject(Constants.DATA).getAsJsonObject(attributeName);
            conversionBlock.add(Constants.WGS84_COORDINATES, convertObj);
            dataBlock.add(attributeName, conversionBlock);
            recordJsonObject.add(Constants.DATA, dataBlock);
        } catch (JsonProcessingException ex) {
            logger.error(String.format("There was an error converting the schema to a JSON string. %s", ex.getMessage(), ex));
        }
    }

    private void overwritePropertyToData(String name, double value, JsonObject data) {
        String[] nestedNames = name.split("\\.");
        JsonObject outter = data;
        JsonObject inner = data;

        for (int i = 0; i < nestedNames.length - 1; i++) {
            inner = outter.getAsJsonObject(nestedNames[i]);
            outter = inner;
        }

        inner.addProperty(nestedNames[nestedNames.length - 1], value);
    }

    private ConversionStatus.ConversionStatusBuilder getConversionStatusBuilderFromList(String recordId, List<ConversionStatus.ConversionStatusBuilder> conversionStatuses) {
        for (int i = 0; i < conversionStatuses.size(); i++) {
            ConversionStatus.ConversionStatusBuilder builder = conversionStatuses.get(i);
            if (builder.getId().equalsIgnoreCase(recordId)) {
                return builder;
            }
        }
        return null;
    }

    private String getRecordId(JsonObject record) {
        JsonElement recordId = record.get("id");
        if (recordId == null || recordId instanceof JsonNull || recordId.getAsString().isEmpty()) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, "Record does not have id.");
        }
        return recordId.getAsString();
    }

    private DpsHeaders customizeHeaderBeforeCallingCrsConversion(DpsHeaders dpsHeaders) {
    	 String token=null;    	
    	 boolean createToken=springConfig.isCreateCrsJWTToken();
   	
    	if (createToken) {
    		token = this.jwtClient.getIdToken(dpsHeaders.getPartitionId());
    		if (Strings.isNullOrEmpty(token)) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, "authorization for crs conversion failed");
    		}
    	}else {

    		token=dpsHeaders.getAuthorization();
    	}
        DpsHeaders headers = DpsHeaders.createFromMap(dpsHeaders.getHeaders());
        headers.put(DpsHeaders.AUTHORIZATION, token);
        headers.put(DpsHeaders.DATA_PARTITION_ID, dpsHeaders.getPartitionId());
        return headers;
    }

    private void setGeometry(String type, GeoJsonFeature feature, JsonObject coordinates, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        Gson gson = new Gson();
        switch (type) {
            case Constants.ANY_CRS_POINT: feature.setGeometry(this.getGeoJsonPoint(gson, coordinates, statusBuilder));
                break;
            case Constants.ANY_CRS_MULTIPOINT: feature.setGeometry(this.getGeoJsonMultiPoint(gson, coordinates, statusBuilder));
                break;
            case Constants.ANY_CRS_LINE_STRING: feature.setGeometry(this.getGeoJsonLineString(gson, coordinates, statusBuilder));
                break;
            case Constants.ANY_CRS_MULTILINE_STRING: feature.setGeometry(this.getGeoJsonMultiLineString(gson, coordinates, statusBuilder));
                break;
            case Constants.ANY_CRS_POLYGON: feature.setGeometry(this.getGeoJsonPolygon(gson, coordinates, statusBuilder));
                break;
            case Constants.ANY_CRS_MULTIPOLYGON: feature.setGeometry(this.getGeoJsonMultiPolygon(gson, coordinates, statusBuilder));
                break;
            default: statusBuilder.addError(String.format(CrsConversionServiceErrorMessages.INVALID_GEOMETRY, type));
                break;
        }
    }

    private GeoJsonFeature getFeature(JsonObject featureItem, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        GeoJsonFeature feature = new GeoJsonFeature();
        if (conversionJsonUtils.isJsonObjectContainsProperty(featureItem, TYPE)) feature.setType(featureItem.get(TYPE).getAsString());
        if (conversionJsonUtils.isJsonObjectContainsProperty(featureItem, PROPERTIES) && conversionJsonUtils.isPropertyJsonObject(featureItem, PROPERTIES, statusBuilder))  feature.setProperties(featureItem.getAsJsonObject(PROPERTIES));
        if (conversionJsonUtils.isJsonObjectContainsProperty(featureItem, BBOX) && conversionJsonUtils.isPropertyJsonArray(featureItem, BBOX, statusBuilder)) feature.setBbox(this.bboxValues(featureItem.getAsJsonArray(BBOX)));

        if (conversionJsonUtils.isJsonObjectContainsProperty(featureItem, GEOMETRY) && conversionJsonUtils.isPropertyJsonObject(featureItem, GEOMETRY, statusBuilder)) {
            JsonObject geometry = featureItem.getAsJsonObject(GEOMETRY);
            String geometryType = conversionJsonUtils.isJsonObjectContainsProperty(geometry, TYPE) ? geometry.get(TYPE).getAsString() : "";

            if (geometryType.equals(ANY_CRS_GEOMETRY_COLLECTION)) {
                JsonArray geometriesArray = conversionJsonUtils.isJsonObjectContainsProperty(geometry, GEOMETRIES) && conversionJsonUtils.isPropertyJsonArray(geometry, GEOMETRIES, statusBuilder) ? geometry.get(GEOMETRIES).getAsJsonArray() : new JsonArray();
                if (geometriesArray == null || geometriesArray.size() == 0) {
                    statusBuilder.addError(MISSING_GEOMETRIES);
                } else {
                    GeoJsonGeometryCollection gc = new GeoJsonGeometryCollection();
                    GeoJsonBase[] geometries = new GeoJsonBase[geometriesArray.size()];
                    for (int k = 0; k < geometriesArray.size(); k++) {
                        JsonObject geometryObj = geometriesArray.get(k).getAsJsonObject();
                        String geometriesType = conversionJsonUtils.isJsonObjectContainsProperty(geometryObj, TYPE) ? geometryObj.get(TYPE).getAsString() : "";
                        JsonArray coordinatesValues = conversionJsonUtils.isJsonObjectContainsProperty(geometryObj, COORDINATES) && conversionJsonUtils.isPropertyJsonArray(geometryObj, COORDINATES, statusBuilder) ? geometryObj.get(COORDINATES).getAsJsonArray() : new JsonArray();
                        JsonObject gmCoordinatesObj = this.getCoordinates(coordinatesValues, statusBuilder);
                        Gson gson = new Gson();
                        switch (geometriesType) {
                            case POINT: geometries[k] = this.getGeoJsonPoint(gson, gmCoordinatesObj, statusBuilder);
                                break;
                            case MULTIPOINT: geometries[k] = this.getGeoJsonMultiPoint(gson, gmCoordinatesObj, statusBuilder);
                                break;
                            case LINE_STRING: geometries[k] = this.getGeoJsonLineString(gson, gmCoordinatesObj, statusBuilder);
                                break;
                            case MULTILINE_STRING: geometries[k] = this.getGeoJsonMultiLineString(gson, gmCoordinatesObj, statusBuilder);
                                break;
                            case POLYGON: geometries[k] = this.getGeoJsonPolygon(gson, gmCoordinatesObj, statusBuilder);
                                break;
                            case MULTIPOLYGON: geometries[k] = this.getGeoJsonMultiPolygon(gson, gmCoordinatesObj, statusBuilder);
                                break;
                            default: statusBuilder.addError(String.format(CrsConversionServiceErrorMessages.INVALID_GEOMETRIES, geometriesType));
                                break;
                        }
                        geometries[k].setType(geometriesType);
                        gc.setGeometries(geometries);
                        feature.setGeometry(gc);
                    }
                }
            } else {
                JsonArray coordinatesValues = conversionJsonUtils.isJsonObjectContainsProperty(geometry, COORDINATES) && conversionJsonUtils.isPropertyJsonArray(geometry, COORDINATES, statusBuilder) ? geometry.get(COORDINATES).getAsJsonArray() : new JsonArray();
                JsonObject coordinatesObj = this.getCoordinates(coordinatesValues, statusBuilder);
                this.setGeometry(geometryType, feature, coordinatesObj, statusBuilder);
            }
        } else {
            statusBuilder.addError(CrsConversionServiceErrorMessages.MISSING_GEOMETRY);
        }
        return feature;
    }

    private GeoJsonPoint getGeoJsonPoint(Gson gson, JsonObject coordinatesObj, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        GeoJsonPoint point = new GeoJsonPoint();
        try {
            point =  gson.fromJson(coordinatesObj, GeoJsonPoint.class);
        } catch (JsonSyntaxException jsonEx) {
            statusBuilder.addError(String.format(INVALID_COORDINATES, jsonEx.getMessage()));
        }
        return point;
    }

    private GeoJsonMultiPoint getGeoJsonMultiPoint(Gson gson, JsonObject coordinatesObj, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        GeoJsonMultiPoint multiPoint = new GeoJsonMultiPoint();
        try {
            multiPoint =  gson.fromJson(coordinatesObj, GeoJsonMultiPoint.class);
        } catch (JsonSyntaxException jsonEx) {
            statusBuilder.addError(String.format(INVALID_COORDINATES, jsonEx.getMessage()));
        }
        return multiPoint;
    }

    private GeoJsonLineString getGeoJsonLineString(Gson gson, JsonObject coordinatesObj, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        GeoJsonLineString lintString = new GeoJsonLineString();
        try {
            lintString =  gson.fromJson(coordinatesObj, GeoJsonLineString.class);
        } catch (JsonSyntaxException jsonEx) {
            statusBuilder.addError(String.format(INVALID_COORDINATES, jsonEx.getMessage()));
        }
        return lintString;
    }

    private GeoJsonMultiLineString getGeoJsonMultiLineString(Gson gson, JsonObject coordinatesObj, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        GeoJsonMultiLineString multiLintString = new GeoJsonMultiLineString();
        try {
            multiLintString =  gson.fromJson(coordinatesObj, GeoJsonMultiLineString.class);
        } catch (JsonSyntaxException jsonEx) {
            statusBuilder.addError(String.format(INVALID_COORDINATES, jsonEx.getMessage()));
        }
        return multiLintString;
    }

    private GeoJsonPolygon getGeoJsonPolygon(Gson gson, JsonObject coordinatesObj, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        GeoJsonPolygon polygon = new GeoJsonPolygon();
        try {
            polygon =  gson.fromJson(coordinatesObj, GeoJsonPolygon.class);
        } catch (JsonSyntaxException jsonEx) {
            statusBuilder.addError(String.format(INVALID_COORDINATES, jsonEx.getMessage()));
        }
        return polygon;
    }

    private GeoJsonMultiPolygon getGeoJsonMultiPolygon(Gson gson, JsonObject coordinatesObj, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        GeoJsonMultiPolygon multiPolygon = new GeoJsonMultiPolygon();
        try {
            multiPolygon =  gson.fromJson(coordinatesObj, GeoJsonMultiPolygon.class);
        } catch (JsonSyntaxException jsonEx) {
            statusBuilder.addError(String.format(INVALID_COORDINATES, jsonEx.getMessage()));
        }
        return multiPolygon;
    }

    private JsonObject getCoordinates(JsonArray coordinates, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        JsonObject coordinatesObj = new JsonObject();
        if (coordinates.size() > 0) {
            coordinatesObj.add(COORDINATES, coordinates);
        } else {
            statusBuilder.addError(CrsConversionServiceErrorMessages.MISSING_COORDINATES);
        }
        return coordinatesObj;
    }

    private double[] bboxValues(JsonArray bboxValues) {
        double[] bbox = new double[bboxValues.size()];
        for (int i = 0; i < bboxValues.size(); i++) {
            bbox[i] = bboxValues.get(i).getAsDouble();
        }
        return bbox;
    }

    private RequestConfig getRequestConfig(){
        return RequestConfig.custom()
                .setSocketTimeout(crsConversionConfig.getSocketTimeout())
                .setConnectTimeout(crsConversionConfig.getConnectTimeout())
                .setConnectionRequestTimeout(crsConversionConfig.getConnectionRequestTimeout())
                .build();
    }
}
