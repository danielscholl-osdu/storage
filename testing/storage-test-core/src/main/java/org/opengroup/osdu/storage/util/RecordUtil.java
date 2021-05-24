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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class RecordUtil {

    public static String createDefaultJsonRecord(String id, String kind, String legalTag) {
        JsonObject record = getDefaultRecordWithDefaultData(id, kind, legalTag);
        JsonArray records = new JsonArray();
        records.add(record);
        return records.toString();
    }

    public static String createDefaultJsonRecords(int recordsCount, String id, String kind, String legalTag) {
        JsonArray records = new JsonArray();
        for (int i = 0; i < recordsCount; i++) {
            JsonObject record = getDefaultRecordWithDefaultData(id +i, kind, legalTag);
            records.add(record);
        }
        return records.toString();
    }

	public static String createJsonRecordWithData(String id, String kind, String legalTag, String data) {

		JsonObject dataJson = new JsonObject();
		dataJson.addProperty("custom", data);
		dataJson.addProperty("score-int", 58377304471659395L);
		dataJson.addProperty("score-double", 58377304.471659395);

		JsonObject record = getRecordWithInputData(id, kind, legalTag, dataJson);

		JsonArray records = new JsonArray();
		records.add(record);

		return records.toString();
	}

	public static String createJsonRecordWithEntV2OnlyAcl(String id, String kind, String legalTag, String data) {
		JsonObject dataJson = new JsonObject();
		dataJson.addProperty("custom", data);
		dataJson.addProperty("score-int", 58377304471659395L);
		dataJson.addProperty("score-double", 58377304.471659395);

		JsonObject record = getRecordWithInputDataAndAcl(id, kind, legalTag, dataJson);

		JsonArray records = new JsonArray();
		records.add(record);

		return records.toString();

	}

	public static String createJsonRecordWithReference(int recordsCount, String id, String kind, String legalTag, String fromCrs, String conversionType) {

		JsonArray records = new JsonArray();

		for (int i = 0; i < recordsCount; i++) {

			JsonObject data = new JsonObject();
			data.addProperty("X", 16.00);
			data.addProperty("Y", 10.00);
			data.addProperty("Z", 0.0);

			JsonArray propertyNames = new JsonArray();
			propertyNames.add("X");
			propertyNames.add("Y");
			propertyNames.add("Z");

			JsonObject meta = new JsonObject();
			meta.addProperty("kind", conversionType);
			meta.addProperty("persistableReference", fromCrs);
			meta.add("propertyNames", propertyNames);

			JsonArray metaBlocks = new JsonArray();
			metaBlocks.add(meta);

			JsonObject record = getRecordWithInputData(id + i, kind, legalTag, data);
			record.add("meta", metaBlocks);

			records.add(record);
		}

		return records.toString();
	}

	public static String createJsonRecordMissingValue(int recordsCount, String id, String kind, String legalTag, String fromCrs, String conversionType) {

		JsonArray records = new JsonArray();

		for (int i = 0; i < recordsCount; i++) {

			JsonObject data = new JsonObject();
			data.addProperty("X", 16.00);
			data.addProperty("Z", 0.0);

			JsonArray propertyNames = new JsonArray();
			propertyNames.add("X");
			propertyNames.add("Y");
			propertyNames.add("Z");

			JsonObject meta = new JsonObject();
			meta.addProperty("kind", conversionType);
			meta.addProperty("persistableReference", fromCrs);
			meta.add("propertyNames", propertyNames);

			JsonArray metaBlocks = new JsonArray();
			metaBlocks.add(meta);

			JsonObject record = getRecordWithInputData(id + i, kind, legalTag, data);
			record.add("meta", metaBlocks);

			records.add(record);
		}

		return records.toString();
	}

	public static String createJsonRecordNoMetaBlock(int recordsCount, String id, String kind, String legalTag) {

		JsonArray records = new JsonArray();

		for (int i = 0; i < recordsCount; i++) {
			JsonObject data = new JsonObject();
			data.addProperty("X", 16.00);
			data.addProperty("Y", 16.00);
			data.addProperty("Z", 0.0);

			JsonObject record = getRecordWithInputData(id + i, kind, legalTag, data);
			records.add(record);
		}

		return records.toString();
	}

	public static String createJsonRecordsWithDateFormat(int recordsCount, String id, String kind, String legalTag, String format, String propertyName, String date, String persistableReference) {

		JsonArray records = new JsonArray();

		for (int i = 0; i < recordsCount; i++) {
			JsonObject data = new JsonObject();
			data.addProperty(propertyName, date);

			JsonArray propertyNames = new JsonArray();
			propertyNames.add(propertyName);

			JsonObject meta = new JsonObject();
			meta.addProperty("persistableReference", persistableReference);
			meta.addProperty("kind", "DateTime");
			meta.add("propertyNames", propertyNames);

			JsonArray metas = new JsonArray();
			metas.add(meta);

			JsonObject record = getRecordWithInputData(id + i, kind, legalTag, data);
			record.add("meta", metas);
			records.add(record);
		}

		return records.toString();
	}

	public static String createJsonRecordWithNestedProperty(int recordsNumber, String id, String kind, String legalTag, String fromCrs, String conversionType) {

		JsonArray records = new JsonArray();

		for (int i = 0; i < 8 + recordsNumber; i++) {

			JsonArray pointValues1 = new JsonArray();
			pointValues1.add(16.00);
			pointValues1.add(10.00);
			JsonArray pointValues2 = new JsonArray();
			pointValues2.add(16.00);
			pointValues2.add(10.00);
			JsonArray points = new JsonArray();
			points.add(pointValues1);
			points.add(pointValues2);

			JsonObject nestedProperty = new JsonObject();
			nestedProperty.addProperty("crsKey", "Native");
			nestedProperty.add("points", points);

			JsonObject data = new JsonObject();
			data.addProperty("message", "integration-test-record");
			data.add("projectOutlineLocalGeographic", nestedProperty);

			JsonArray propertyNames = new JsonArray();
			propertyNames.add("projectOutlineLocalGeographic");

			JsonObject meta = new JsonObject();
			meta.addProperty("kind", conversionType);
			meta.addProperty("persistableReference", fromCrs);
			meta.add("propertyNames", propertyNames);

			JsonArray metaBlocks = new JsonArray();
			metaBlocks.add(meta);

			JsonObject record = getRecordWithInputData(id + i, kind, legalTag, data);
			record.add("meta", metaBlocks);

			records.add(record);
		}

		return records.toString();
	}

	public static String createJsonRecordWithMultiplePairOfCoordinates(int recordsNumber, String id, String kind, String legalTag, String fromCrs, String conversionType) {

		JsonArray records = new JsonArray();

		for (int i = 0; i <  recordsNumber; i++) {

			JsonObject data = new JsonObject();
			data.addProperty("X", 16.00);
			data.addProperty("Y", 10.00);
			data.addProperty("LON", 16.00);
			data.addProperty("LAT", 10.00);

			JsonArray propertyNames = new JsonArray();
			propertyNames.add("X");
			propertyNames.add("Y");
			propertyNames.add("LON");
			propertyNames.add("LAT");

			JsonObject meta = new JsonObject();
			meta.addProperty("kind", conversionType);
			meta.addProperty("persistableReference", fromCrs);
			meta.add("propertyNames", propertyNames);

			JsonArray metaBlocks = new JsonArray();
			metaBlocks.add(meta);

			JsonObject record = getDefaultRecord(id + i, kind, legalTag);
			record.add("data", data);
			record.add("meta", metaBlocks);

			records.add(record);
		}

		return records.toString();
	}

	private static JsonObject getDefaultRecord(String id, String kind, String legalTag) {
		JsonArray acls = new JsonArray();
		acls.add(TestUtils.getAcl());
		return getDefaultRecordFromAcl(id, kind, legalTag, acls);
	}

	private static JsonObject getDefaultRecordWithEntV2OnlyAcl(String id, String kind, String legalTag) {
		JsonArray acls = new JsonArray();
		acls.add(TestUtils.getEntV2OnlyAcl());
		return getDefaultRecordFromAcl(id, kind, legalTag, acls);
	}

	private static JsonObject getDefaultRecordFromAcl(String id, String kind, String legalTag, JsonArray acls) {
		JsonObject acl = new JsonObject();
		acl.add("viewers", acls);
		acl.add("owners", acls);

		JsonArray tags = new JsonArray();
		tags.add(legalTag);

		JsonArray ordcJson = new JsonArray();
		ordcJson.add("BR");

		JsonObject legal = new JsonObject();
		legal.add("legaltags", tags);
		legal.add("otherRelevantDataCountries", ordcJson);

		JsonObject record = new JsonObject();
		record.addProperty("id", id);
		record.addProperty("kind", kind);
		record.add("acl", acl);
		record.add("legal", legal);
		return record;
	}

	private static JsonObject getDefaultRecordWithDefaultData(String id, String kind, String legalTag) {
		JsonObject data = new JsonObject();
		data.add("int-tag", getNumberPropertyObject("score-int", 58377304471659395L));
		data.add("double-tag", getNumberPropertyObject("score-double", 58377304.471659395));
		data.addProperty("count", 123456789L);
		JsonObject record = getRecordWithInputData(id, kind, legalTag, data);
		return record;
	}

	private static JsonObject getRecordWithInputData(String id, String kind, String legalTag, JsonObject data) {
		JsonObject record = getDefaultRecord(id, kind, legalTag);
		record.add("data", data);
		return record;
	}

	private static JsonObject getRecordWithInputDataAndAcl(String id, String kind, String legalTag, JsonObject data) {
		JsonObject record = getDefaultRecordWithEntV2OnlyAcl(id, kind, legalTag);
		record.add("data", data);
		return record;
	}

	private static JsonObject getNumberPropertyObject(String propertyName, Number intValue) {
		JsonObject numberProperty = new JsonObject();
		numberProperty.addProperty(propertyName, intValue);
		return numberProperty;
	}
}