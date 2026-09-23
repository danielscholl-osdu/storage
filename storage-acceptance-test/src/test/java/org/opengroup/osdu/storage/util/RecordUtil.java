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

package org.opengroup.osdu.storage.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.test.client.model.storage.PatchOperation;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.client.model.storage.RecordAcl;
import org.opengroup.osdu.core.test.client.model.storage.RecordAncestry;
import org.opengroup.osdu.core.test.client.model.storage.RecordLegal;
import org.opengroup.osdu.core.test.client.model.storage.UpdateRecordsMetadataRequest;
import org.opengroup.osdu.core.test.client.model.storage.UpdateRecordsQuery;

public class RecordUtil {

  private static final String UNIT_OF_MEASURE_ID = "unitOfMeasureID";

  public static StorageRecord[] createDefaultRecords(String id, String kind, String legalTag) {
    return single(getDefaultRecordWithDefaultData(id, kind, legalTag));
  }

  public static StorageRecord[] createRecordsWithDuplicateAclAndLegaltags(String id, String kind,
      String legalTag) {
    return single(getDefaultRecordWithDefaultDataAndDuplicateAclAndLegaltags(id, kind, legalTag));
  }

  public static StorageRecord[] createDefaultRecordsWithParentId(String id, String kind, String legalTag,
      String parentId) {
    StorageRecord record = getDefaultRecordWithDefaultData(id, kind, legalTag);
    return single(withAncestry(record, new RecordAncestry(new String[] {parentId})));
  }

  public static StorageRecord[] createDefaultRecords(int recordsCount, String id, String kind,
      String legalTag) {
    StorageRecord[] records = new StorageRecord[recordsCount];
    for (int i = 0; i < recordsCount; i++) {
      records[i] = getDefaultRecordWithDefaultData(id + i, kind, legalTag);
    }
    return records;
  }

  public static StorageRecord[] createRecordsWithData(String id, String kind, String legalTag,
      String data) {
    Map<String, Object> dataMap = new LinkedHashMap<>();
    dataMap.put("custom", data);
    dataMap.put("score-int", 58377304471659395L);
    dataMap.put("score-double", 58377304.471659395);
    return single(getRecordWithInputData(id, kind, legalTag, dataMap));
  }

  public static StorageRecord[] createRecordForUnitConversionWithPrimitiveArray(String id, String kind,
      String legalTag, String unitOfMeasureId, String fileName) throws IOException {
    StorageRecord fromFile = FileReadUtil.readRecordFromFile(fileName);
    List<Map<String, Object>> meta = copyMeta(fromFile.meta());
    Map<String, Object> firstMeta = new LinkedHashMap<>(meta.get(0));
    firstMeta.put(UNIT_OF_MEASURE_ID, unitOfMeasureId);
    meta.set(0, firstMeta);
    StorageRecord base = getDefaultRecord(id, kind, legalTag);
    return single(withDataAndMeta(base, fromFile.data(), meta));
  }

  public static StorageRecord[] createRecordsWithEntV2OnlyAcl(String id, String kind, String legalTag,
      String data) {
    Map<String, Object> dataMap = new LinkedHashMap<>();
    dataMap.put("custom", data);
    dataMap.put("score-int", 58377304471659395L);
    dataMap.put("score-double", 58377304.471659395);
    return single(getRecordWithInputDataAndAcl(id, kind, legalTag, dataMap));
  }

  public static StorageRecord[] createRecordsWithReference(int recordsCount, String id, String kind,
      String legalTag, String fromCrs, String conversionType) {
    List<StorageRecord> records = new ArrayList<>();
    for (int i = 0; i < recordsCount; i++) {
      Map<String, Object> data = crsData(16.00, 10.00, 0.0);
      List<Map<String, Object>> meta = List.of(
          metaBlock(conversionType, fromCrs, "X", "Y", "Z"));
      records.add(withDataAndMeta(getDefaultRecord(id + i, kind, legalTag), data, meta));
    }
    return toArray(records);
  }

  public static StorageRecord[] createRecordsMissingValue(int recordsCount, String id, String kind,
      String legalTag, String fromCrs, String conversionType) {
    List<StorageRecord> records = new ArrayList<>();
    for (int i = 0; i < recordsCount; i++) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("X", 16.00);
      data.put("Z", 0.0);
      List<Map<String, Object>> meta = List.of(
          metaBlock(conversionType, fromCrs, "X", "Y", "Z"));
      records.add(withDataAndMeta(getDefaultRecord(id + i, kind, legalTag), data, meta));
    }
    return toArray(records);
  }

  public static StorageRecord[] createRecordsNoMetaBlock(int recordsCount, String id, String kind,
      String legalTag) {
    List<StorageRecord> records = new ArrayList<>();
    for (int i = 0; i < recordsCount; i++) {
      Map<String, Object> data = crsData(16.00, 16.00, 0.0);
      records.add(getRecordWithInputData(id + i, kind, legalTag, data));
    }
    return toArray(records);
  }

  public static StorageRecord[] createRecordsWithDateFormat(int recordsCount, String id, String kind,
                                                     String legalTag, String propertyName, String date,
                                                     String persistableReference) {
    List<StorageRecord> records = new ArrayList<>();
    for (int i = 0; i < recordsCount; i++) {
      Map<String, Object> data = Map.of(propertyName, date);
      List<Map<String, Object>> meta = List.of(metaBlockDateTime(persistableReference, propertyName));
      records.add(withDataAndMeta(getDefaultRecord(id + i, kind, legalTag), data, meta));
    }
    return toArray(records);
  }

  public static StorageRecord[] createRecordsWithNestedProperty(int recordsNumber, String id, String kind,
      String legalTag, String fromCrs, String conversionType) {
    List<StorageRecord> records = new ArrayList<>();
    for (int i = 0; i < 8 + recordsNumber; i++) {
      List<List<Double>> points = List.of(
          List.of(16.00, 10.00),
          List.of(16.00, 10.00));
      Map<String, Object> nestedProperty = new LinkedHashMap<>();
      nestedProperty.put("crsKey", "Native");
      nestedProperty.put(Constants.POINTS, points);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("message", "integration-test-record");
      data.put("projectOutlineLocalGeographic", nestedProperty);
      List<Map<String, Object>> meta = List.of(
          metaBlock(conversionType, fromCrs, "projectOutlineLocalGeographic"));
      records.add(withDataAndMeta(getDefaultRecord(id + i, kind, legalTag), data, meta));
    }
    return toArray(records);
  }

  public static StorageRecord[] createRecordsWithNestedArrayOfProperties(int recordsNumber, String id,
      String kind, String legalTag, String fromRef, String conversionType,
      String unitOfMeasureID) {
    return createRecordsWithNestedArray(recordsNumber, id, kind, legalTag, fromRef, conversionType,
        unitOfMeasureID, "markers[].measuredDepth", 12, 10.0, 20.0);
  }

  public static StorageRecord[] createRecordsWithNestedArrayOfPropertiesAndInvalidValues(
      int recordsNumber, String id, String kind, String legalTag, String fromRef,
      String conversionType, String unitOfMeasureID) {
    return createRecordsWithNestedArray(recordsNumber, id, kind, legalTag, fromRef, conversionType,
        unitOfMeasureID, "markers[].measuredDepth", 12, 10.0, "invalidValue");
  }

  public static StorageRecord[] createRecordsWithInhomogeneousNestedArrayOfProperties(int recordsNumber,
      String id, String kind, String legalTag, String fromRef, String conversionType,
      String unitOfMeasureID) {
    return createRecordsWithNestedArray(recordsNumber, id, kind, legalTag, fromRef, conversionType,
        unitOfMeasureID, "markers[1].measuredDepth", 13, 10.0, 20.0);
  }

  public static StorageRecord[] createRecordsWithInhomogeneousNestedArrayOfPropertiesAndInvalidValues(
      int recordsNumber, String id, String kind, String legalTag, String fromRef,
      String conversionType, String unitOfMeasureID) {
    return createRecordsWithNestedArray(recordsNumber, id, kind, legalTag, fromRef, conversionType,
        unitOfMeasureID, "markers[1].measuredDepth", 13, 10.0, "invalidValue");
  }

  public static StorageRecord[] createRecordsWithInhomogeneousNestedArrayOfPropertiesAndIndexOutOfBoundary(
      int recordsNumber, String id, String kind, String legalTag, String fromRef,
      String conversionType, String unitOfMeasureID) {
    return createRecordsWithNestedArray(recordsNumber, id, kind, legalTag, fromRef, conversionType,
        unitOfMeasureID, "markers[2].measuredDepth", 13, 10.0, "20.0");
  }

  public static StorageRecord[] createRecordsWithMultiplePairOfCoordinates(int recordsNumber, String id,
      String kind, String legalTag, String fromCrs, String conversionType) {
    List<StorageRecord> records = new ArrayList<>();
    for (int i = 0; i < recordsNumber; i++) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("X", 16.00);
      data.put("Y", 10.00);
      data.put("LON", 16.00);
      data.put("LAT", 10.00);
      List<Map<String, Object>> meta = List.of(
          metaBlock(conversionType, fromCrs, "X", "Y", "LON", "LAT"));
      StorageRecord base = getDefaultRecord(id + i, kind, legalTag);
      records.add(withDataAndMeta(base, data, meta));
    }
    return toArray(records);
  }

  public static StorageRecord[] createRecordsWithAsIngestedCoordinates(int recordsNumber, String id,
      String kind, String legalTag, String prCRS, String prUNITZ, String geometryType,
      String attributeType) {
    List<StorageRecord> records = new ArrayList<>();
    for (int i = 0; i < recordsNumber; i++) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put(attributeType, Map.of(Constants.AS_INGESTED_COORDINATES,
          buildAsIngestedCoordinates(prCRS, prUNITZ, geometryType, false)));
      StorageRecord base = getDefaultRecord(id + i, kind, legalTag);
      records.add(withData(base, data));
    }
    return toArray(records);
  }

  public static StorageRecord[] createRecordsWithInvalidAsIngestedCoordinates(int recordsNumber,
      String id, String kind, String legalTag, String prCRS, String prUNITZ,
      String geometryType, String attributeType) {
    List<StorageRecord> records = new ArrayList<>();
    for (int i = 0; i < recordsNumber; i++) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put(attributeType, Map.of(Constants.AS_INGESTED_COORDINATES,
          buildAsIngestedCoordinates(prCRS, prUNITZ, geometryType, true)));
      StorageRecord base = getDefaultRecord(id + i, kind, legalTag);
      records.add(withData(base, data));
    }
    return toArray(records);
  }

  public static UpdateRecordsMetadataRequest buildUpdateTagsMetadata(
      String recordId, String op, String value) {
    return new UpdateRecordsMetadataRequest(
        new UpdateRecordsQuery(new String[] {recordId}),
        new PatchOperation[] {new PatchOperation(op, "/tags", new String[] {value})});
  }

  public static UpdateRecordsMetadataRequest buildMetadataPatch(
      String[] recordIds, PatchOperation... ops) {
    return new UpdateRecordsMetadataRequest(new UpdateRecordsQuery(recordIds), ops);
  }

  public static UpdateRecordsMetadataRequest buildMetadataPatch(
      String recordId, PatchOperation... ops) {
    return buildMetadataPatch(new String[] {recordId}, ops);
  }

  public static UpdateRecordsMetadataRequest buildUpdateLegalMetadata(
      String recordId, String op, String legalTag) {
    return buildMetadataPatch(recordId,
        new PatchOperation(op, "/legal/legaltags", new String[] {legalTag}));
  }

  public static UpdateRecordsMetadataRequest buildUpdateAclMetadata(
      String recordId, String op, String path, String acl) {
    return buildMetadataPatch(recordId,
        new PatchOperation(op, path, new String[] {acl}));
  }

  public static UpdateRecordsMetadataRequest buildReplaceAclMetadata(
      String[] recordIds, String acl) {
    return buildMetadataPatch(recordIds,
        new PatchOperation("replace", "/acl/viewers", new String[] {acl}),
        new PatchOperation("replace", "/acl/owners", new String[] {acl}));
  }

  public static StorageRecord[] replaceAcl(StorageRecord[] records, String fromAcl, String toAcl) {
    return Arrays.stream(records)
        .map(record -> replaceAcl(record, fromAcl, toAcl))
        .toArray(StorageRecord[]::new);
  }

  public static StorageRecord replaceAcl(StorageRecord record, String fromAcl, String toAcl) {
    RecordAcl acl = record.acl();
    if (acl == null) {
      return record;
    }
    return new StorageRecord(
        record.id(),
        record.version(),
        record.kind(),
        new RecordAcl(replaceInArray(acl.viewers(), fromAcl, toAcl),
            replaceInArray(acl.owners(), fromAcl, toAcl)),
        record.data(),
        record.legal(),
        record.ancestry(),
        record.tags(),
        record.meta(),
        record.modifyTime(),
        record.modifyUser(),
        record.createTime(),
        record.createUser());
  }

  public static StorageRecord[] createRecordsWithWGS84Coordinates(int recordsNumber, String id,
      String kind, String legalTag, String prCRS, String prUNITZ, String geometryType,
      String attributeType) {
    List<StorageRecord> records = new ArrayList<>();
    for (int i = 0; i < recordsNumber; i++) {
      Map<String, Object> asIngested = buildAsIngestedCoordinates(prCRS, prUNITZ, geometryType,
          false);
      Map<String, Object> wgs84 = buildWgs84Coordinates(prUNITZ);
      Map<String, Object> attribute = new LinkedHashMap<>();
      attribute.put(Constants.AS_INGESTED_COORDINATES, asIngested);
      attribute.put(Constants.WGS84_COORDINATES, wgs84);
      Map<String, Object> data = Map.of(attributeType, attribute);
      StorageRecord base = getDefaultRecord(id + i, kind, legalTag);
      records.add(withData(base, data));
    }
    return toArray(records);
  }

  public static StorageRecord[] createRecordsWithCustomAcl(String newRecordId, String kind,
      String legalTag, String dataGroupEmail) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("score-int", 58377304471659395L);
    data.put("score-double", 58377304.471659395);
    StorageRecord base = getDefaultRecordWithCustomAcl(newRecordId, kind, legalTag, dataGroupEmail);
    return single(withData(base, data));
  }

  private static StorageRecord[] createRecordsWithNestedArray(int recordsNumber, String id, String kind,
      String legalTag, String fromRef, String conversionType, String unitOfMeasureID,
      String propertyName, int startIndex, Object depth1, Object depth2) {
    List<StorageRecord> records = new ArrayList<>();
    for (int i = startIndex; i < startIndex + recordsNumber; i++) {
      List<Map<String, Object>> markers = List.of(
          marker(depth1, "testValue1"),
          marker(depth2, "testValue2"));
      records.add(createRecordWithNestedArray(markers, id + i, kind, legalTag, conversionType,
          fromRef, propertyName, unitOfMeasureID));
    }
    return toArray(records);
  }

  private static Map<String, Object> marker(Object measuredDepth, String otherField) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("measuredDepth", measuredDepth);
    item.put("otherField", otherField);
    return item;
  }

  private static StorageRecord createRecordWithNestedArray(List<Map<String, Object>> markers, String id,
      String kind, String legalTag, String conversionType, String fromRef, String propertyName,
      String unitOfMeasureID) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("message", "integration-test-record");
    data.put("markers", markers);
    Map<String, Object> meta = metaBlock(conversionType, fromRef, propertyName);
    meta.put(UNIT_OF_MEASURE_ID, unitOfMeasureID);
    return withDataAndMeta(getDefaultRecord(id, kind, legalTag), data, List.of(meta));
  }

  private static Map<String, Object> buildAsIngestedCoordinates(String prCRS, String prUNITZ,
      String geometryType, boolean invalid) {
    Map<String, Object> properties = new LinkedHashMap<>();
    Map<String, Object> geometry = buildGeometry(geometryType);
    Map<String, Object> feature = new LinkedHashMap<>();
    feature.put(Constants.BBOX, null);
    feature.put(Constants.TYPE, Constants.ANY_CRS_FEATURE);
    feature.put(Constants.PROPERTIES, properties);
    feature.put(Constants.GEOMETRY, geometry);
    List<Map<String, Object>> features = List.of(feature);

    Map<String, Object> asIngested = new LinkedHashMap<>();
    asIngested.put(Constants.PERSISTABLE_REFERENCE_CRS, prCRS);
    asIngested.put(Constants.PERSISTABLE_REFERENCE_UNIT_Z, prUNITZ);
    asIngested.put(Constants.TYPE, Constants.ANY_CRS_FEATURE_COLLECTION);
    asIngested.put(Constants.PROPERTIES, properties);
    if (invalid) {
      asIngested.put("features1", features);
    } else {
      asIngested.put(Constants.FEATURES, features);
    }
    return asIngested;
  }

  private static Map<String, Object> buildGeometry(String geometryType) {
    Map<String, Object> geometry = new LinkedHashMap<>();
    switch (geometryType) {
      case Constants.ANY_CRS_POINT:
        geometry.put(Constants.COORDINATES, createCoordinates1(1, 2));
        break;
      case Constants.ANY_CRS_MULTIPOINT:
      case Constants.ANY_CRS_LINE_STRING:
        geometry.put(Constants.COORDINATES, createCoordinates2(1, 2));
        break;
      case Constants.ANY_CRS_MULTILINE_STRING:
      case Constants.ANY_CRS_POLYGON:
        geometry.put(Constants.COORDINATES, createCoordinates3(1, 2));
        break;
      case Constants.ANY_CRS_MULTIPOLYGON:
        geometry.put(Constants.COORDINATES, createCoordinates4(1, 2));
        break;
      case Constants.ANY_CRS_GEOMETRY_COLLECTION:
        Map<String, Object> pointGeometry = new LinkedHashMap<>();
        pointGeometry.put(Constants.BBOX, null);
        pointGeometry.put(Constants.TYPE, Constants.POINT);
        pointGeometry.put(Constants.COORDINATES, createCoordinates1(1, 2));
        geometry.put(Constants.GEOMETRIES, List.of(pointGeometry));
        break;
      default:
        geometry.put(Constants.COORDINATES, createCoordinates1(1, 2));
    }
    geometry.put(Constants.TYPE, geometryType);
    geometry.put(Constants.BBOX, null);
    return geometry;
  }

  private static Map<String, Object> buildWgs84Coordinates(String prUNITZ) {
    Map<String, Object> wgsGeometry = new LinkedHashMap<>();
    wgsGeometry.put(Constants.COORDINATES, List.of(5.7500000010406245, 59.000000000399105, 1.9999999999999998));
    wgsGeometry.put(Constants.TYPE, Constants.POINT);
    wgsGeometry.put(Constants.BBOX, null);

    Map<String, Object> wgsFeature = new LinkedHashMap<>();
    wgsFeature.put(Constants.BBOX, null);
    wgsFeature.put(Constants.GEOMETRY, wgsGeometry);

    Map<String, Object> wgs84 = new LinkedHashMap<>();
    wgs84.put(Constants.PERSISTABLE_REFERENCE_CRS, null);
    wgs84.put(Constants.PERSISTABLE_REFERENCE_UNIT_Z, prUNITZ);
    wgs84.put(Constants.TYPE, Constants.FEATURE_COLLECTION);
    wgs84.put(Constants.FEATURES, List.of(wgsFeature));
    return wgs84;
  }

  private static Map<String, Object> crsData(double x, double y, double z) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("X", x);
    data.put("Y", y);
    data.put("Z", z);
    return data;
  }

  private static Map<String, Object> metaBlock(String kind, String persistableReference,
      String... propertyNames) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put(Constants.KIND, kind);
    meta.put(Constants.PERSISTABLE_REFERENCE, persistableReference);
    meta.put(Constants.PROPERTY_NAMES, List.of(propertyNames));
    return meta;
  }

  private static Map<String, Object> metaBlockDateTime(String persistableReference,
      String propertyName) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put(Constants.PERSISTABLE_REFERENCE, persistableReference);
    meta.put(Constants.KIND, "DateTime");
    meta.put(Constants.PROPERTY_NAMES, List.of(propertyName));
    return meta;
  }

  private static List<Map<String, Object>> copyMeta(List<Map<String, Object>> meta) {
    List<Map<String, Object>> copy = new ArrayList<>();
    if (meta != null) {
      for (Map<String, Object> block : meta) {
        copy.add(new LinkedHashMap<>(block));
      }
    }
    return copy;
  }

  private static StorageRecord[] single(StorageRecord record) {
    return new StorageRecord[] {record};
  }

  private static StorageRecord[] toArray(List<StorageRecord> records) {
    return records.toArray(StorageRecord[]::new);
  }

  private static StorageRecord withData(StorageRecord base, Map<String, Object> data) {
    return new StorageRecord(
        base.id(), base.version(), base.kind(), base.acl(), data, base.legal(),
        base.ancestry(), base.tags(), base.meta(), base.modifyTime(), base.modifyUser(),
        base.createTime(), base.createUser());
  }

  private static StorageRecord withDataAndMeta(StorageRecord base, Map<String, Object> data,
      List<Map<String, Object>> meta) {
    return new StorageRecord(
        base.id(), base.version(), base.kind(), base.acl(), data, base.legal(),
        base.ancestry(), base.tags(), meta, base.modifyTime(), base.modifyUser(),
        base.createTime(), base.createUser());
  }

  private static StorageRecord withAncestry(StorageRecord base, RecordAncestry ancestry) {
    return new StorageRecord(
        base.id(), base.version(), base.kind(), base.acl(), base.data(), base.legal(),
        ancestry, base.tags(), base.meta(), base.modifyTime(), base.modifyUser(),
        base.createTime(), base.createUser());
  }

  private static String[] replaceInArray(String[] values, String from, String to) {
    if (values == null) {
      return null;
    }
    String[] result = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = from.equals(values[i]) ? to : values[i];
    }
    return result;
  }

  private static double[][][][] createCoordinates4(int mode, int dimension) {
    double[][][][] pts_s = new double[2][2][5][dimension];
    for (int l = 0; l < 2; l++) {
      for (int k = 0; k < pts_s[0].length; k++) {
        double[][][] pts = createCoordinates3(mode, dimension);
        for (int j = 0; j < pts[0].length; j++) {
          for (int i = 0; i < dimension; i++) {
            pts_s[l][k][j][i] = pts[k][j][i] + (k + l) * 4;
          }
        }
      }
    }
    return pts_s;
  }

  private static double[] createCoordinates1(int mode, int dimension) {
    double[] pt_ac = new double[] {500000, 6500000, 1000};
    double[] pt_gj = new double[] {3, 60, 2000};
    double[] pts = new double[dimension];
    if (mode == 0) {
      System.arraycopy(pt_gj, 0, pts, 0, dimension);
    } else {
      System.arraycopy(pt_ac, 0, pts, 0, dimension);
    }
    return pts;
  }

  private static double[][] createCoordinates2(int mode, int dimension) {
    double[][] s = new double[][] {{-1, 1, 10}, {1, 1, 10}, {1, -1, 20}, {-1, -1, 20}, {-1, 1, 10}};
    double[][] pts = new double[5][dimension];
    double[] pt = createCoordinates1(mode, dimension);
    for (int j = 0; j < 5; j++) {
      for (int i = 0; i < dimension; i++) {
        pts[j][i] = pt[i] + s[j][i];
      }
    }
    return pts;
  }

  private static double[][][] createCoordinates3(int mode, int dimension) {
    double[][][] pts_s = new double[2][5][dimension];
    for (int k = 0; k < pts_s.length; k++) {
      double[][] pts = createCoordinates2(mode, dimension);
      for (int j = 0; j < pts.length; j++) {
        for (int i = 0; i < dimension; i++) {
          pts_s[k][j][i] = pts[j][i] + k * 4;
        }
      }
    }
    return pts_s;
  }

  private static StorageRecord getDefaultRecord(String id, String kind, String legalTag) {
    return getDefaultRecordFromAcl(id, kind, legalTag, acl(TestUtils.getAcl()));
  }

  private static StorageRecord getDefaultRecordWithDuplicateAclsAndLegaltags(String id, String kind,
      String legalTag) {
    String acl = TestUtils.getAcl();
    return getDefaultRecordWithDuplicateAclAndLegaltags(id, kind, legalTag,
        acl(acl, acl));
  }

  private static StorageRecord getDefaultRecordWithCustomAcl(String id, String kind, String legalTag,
      String acl) {
    return getDefaultRecordFromAcl(id, kind, legalTag, acl(acl));
  }

  private static StorageRecord getDefaultRecordWithEntV2OnlyAcl(String id, String kind, String legalTag) {
    return getDefaultRecordFromAcl(id, kind, legalTag, acl(TestUtils.getEntV2OnlyAcl()));
  }

  private static RecordAcl acl(String... viewersAndOwners) {
    return new RecordAcl(viewersAndOwners, viewersAndOwners);
  }

  private static StorageRecord getDefaultRecordFromAcl(String id, String kind, String legalTag,
      RecordAcl acl) {
    return new StorageRecord(id, null, kind, acl, null, defaultLegal(legalTag), null, null, null, null,
        null, null, null);
  }

  private static StorageRecord getDefaultRecordWithDuplicateAclAndLegaltags(String id, String kind,
      String legalTag, RecordAcl acl) {
    return new StorageRecord(id, null, kind, acl, null, duplicateLegal(legalTag), null, null, null, null,
        null, null, null);
  }

  private static RecordLegal defaultLegal(String legalTag) {
    return new RecordLegal(new String[] {legalTag}, new String[] {"BR"});
  }

  private static RecordLegal duplicateLegal(String legalTag) {
    return new RecordLegal(new String[] {legalTag, legalTag}, new String[] {"BR"});
  }

  private static StorageRecord getDefaultRecordWithDefaultData(String id, String kind, String legalTag) {
    return getRecordWithInputData(id, kind, legalTag, defaultData());
  }

  private static StorageRecord getDefaultRecordWithDefaultDataAndDuplicateAclAndLegaltags(String id,
      String kind, String legalTag) {
    return getRecordWithInputDataAndDuplicateAclsAndLegaltags(id, kind, legalTag, defaultData());
  }

  private static Map<String, Object> defaultData() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("int-tag", numberProperty("score-int", 58377304471659395L));
    data.put("double-tag", numberProperty("score-double", 58377304.471659395));
    data.put("count", 123456789L);
    return data;
  }

  private static Map<String, Object> numberProperty(String propertyName, Number value) {
    return Map.of(propertyName, value);
  }

  private static StorageRecord getRecordWithInputData(String id, String kind, String legalTag,
      Map<String, Object> data) {
    return withData(getDefaultRecord(id, kind, legalTag), data);
  }

  private static StorageRecord getRecordWithInputDataAndDuplicateAclsAndLegaltags(String id, String kind,
      String legalTag, Map<String, Object> data) {
    return withData(getDefaultRecordWithDuplicateAclsAndLegaltags(id, kind, legalTag), data);
  }

  private static StorageRecord getRecordWithInputDataAndAcl(String id, String kind, String legalTag,
      Map<String, Object> data) {
    return withData(getDefaultRecordWithEntV2OnlyAcl(id, kind, legalTag), data);
  }
}
