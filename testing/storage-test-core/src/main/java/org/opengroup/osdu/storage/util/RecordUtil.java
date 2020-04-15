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

	public static String createJsonRecord(String id, String kind, String legalTag) {

		JsonObject scoreInt = new JsonObject();
		scoreInt.addProperty("score-int", 58377304471659395L);

		JsonObject scoreDouble = new JsonObject();
		scoreDouble.addProperty("score-double", 58377304.471659395);

		JsonObject data = new JsonObject();
		data.add("int-tag", scoreInt);
		data.add("double-tag", scoreDouble);
		data.addProperty("count", 123456789L);

		JsonObject acl = new JsonObject();
		JsonArray acls = new JsonArray();
		acls.add(TestUtils.getAcl());
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
		record.add("data", data);

		JsonArray records = new JsonArray();
		records.add(record);

		return records.toString();
	}

	public static String createJsonRecord(int recordsNumber, String id, String kind, String legalTag) {

		JsonArray records = new JsonArray();

		for (int i = 0; i < recordsNumber; i++) {
			JsonObject scoreInt = new JsonObject();
			scoreInt.addProperty("score-int", 58377304471659395L);

			JsonObject scoreDouble = new JsonObject();
			scoreDouble.addProperty("score-double", 58377304.471659395);

			JsonObject data = new JsonObject();
			data.add("int-tag", scoreInt);
			data.add("double-tag", scoreDouble);
			data.addProperty("count", 123456789L);

			JsonObject acl = new JsonObject();
			JsonArray acls = new JsonArray();
			acls.add(TestUtils.getAcl());
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
			record.addProperty("id", id + i);
			record.addProperty("kind", kind);
			record.add("acl", acl);
			record.add("legal", legal);
			record.add("data", data);

			records.add(record);
		}

		return records.toString();
	}

	public static String createJsonRecord(String id, String kind, String legalTag, String data) {

		JsonObject dataJson = new JsonObject();
		dataJson.addProperty("custom", data);
		dataJson.addProperty("score-int", 58377304471659395L);
		dataJson.addProperty("score-double", 58377304.471659395);

		JsonObject acl = new JsonObject();
		JsonArray acls = new JsonArray();
		acls.add(TestUtils.getAcl());
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
		record.add("data", dataJson);

		JsonArray records = new JsonArray();
		records.add(record);

		return records.toString();
	}

	public static String createJsonRecord(int recordsNumber, String id, String kind, String legalTag, String fromCrs, String conversionType) {

		JsonArray records = new JsonArray();

		for (int i = 0; i < recordsNumber; i++) {

			JsonObject data = new JsonObject();
			data.addProperty("X", 16.00);
			data.addProperty("Y", 10.00);
			data.addProperty("Z", 0.0);

			JsonObject acl = new JsonObject();
			JsonArray acls = new JsonArray();
			acls.add(TestUtils.getAcl());
			acl.add("viewers", acls);
			acl.add("owners", acls);

			JsonArray tags = new JsonArray();
			tags.add(legalTag);

			JsonArray ordcJson = new JsonArray();
			ordcJson.add("BR");

			JsonObject legal = new JsonObject();
			legal.add("legaltags", tags);
			legal.add("otherRelevantDataCountries", ordcJson);

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

			JsonObject record = new JsonObject();
			record.addProperty("id", id + i);
			record.addProperty("kind", kind);
			record.add("acl", acl);
			record.add("legal", legal);
			record.add("data", data);
			record.add("meta", metaBlocks);

			records.add(record);
		}

		return records.toString();
	}

	public static String createJsonRecordMisingValue(int recordsNumber, String id, String kind, String legalTag, String fromCrs, String conversionType) {

		JsonArray records = new JsonArray();

		for (int i = 4; i < 4 + recordsNumber; i++) {

			JsonObject data = new JsonObject();
			data.addProperty("X", 16.00);
			data.addProperty("Z", 0.0);

			JsonObject acl = new JsonObject();
			JsonArray acls = new JsonArray();
			acls.add(TestUtils.getAcl());
			acl.add("viewers", acls);
			acl.add("owners", acls);

			JsonArray tags = new JsonArray();
			tags.add(legalTag);

			JsonArray ordcJson = new JsonArray();
			ordcJson.add("BR");

			JsonObject legal = new JsonObject();
			legal.add("legaltags", tags);
			legal.add("otherRelevantDataCountries", ordcJson);

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

			JsonObject record = new JsonObject();
			record.addProperty("id", id + i);
			record.addProperty("kind", kind);
			record.add("acl", acl);
			record.add("legal", legal);
			record.add("data", data);
			record.add("meta", metaBlocks);

			records.add(record);
		}

		return records.toString();
	}

	public static String createJsonRecordNoMetaBlock(int recordsNumber, String id, String kind, String legalTag, String fromCrs, String conversionType) {

		JsonArray records = new JsonArray();

		for (int i = 6; i < 6 + recordsNumber; i++) {

			JsonObject data = new JsonObject();
			data.addProperty("X", 16.00);
			data.addProperty("Y", 16.00);
			data.addProperty("Z", 0.0);

			JsonObject acl = new JsonObject();
			JsonArray acls = new JsonArray();
			acls.add(TestUtils.getAcl());
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
			record.addProperty("id", id + i);
			record.addProperty("kind", kind);
			record.add("acl", acl);
			record.add("legal", legal);
			record.add("data", data);

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

			JsonObject acl = new JsonObject();
			JsonArray acls = new JsonArray();
			acls.add(TestUtils.getAcl());
			acl.add("viewers", acls);
			acl.add("owners", acls);

			JsonArray tags = new JsonArray();
			tags.add(legalTag);

			JsonArray ordcJson = new JsonArray();
			ordcJson.add("BR");

			JsonObject legal = new JsonObject();
			legal.add("legaltags", tags);
			legal.add("otherRelevantDataCountries", ordcJson);

			JsonArray propertyNames = new JsonArray();
			propertyNames.add("projectOutlineLocalGeographic");

			JsonObject meta = new JsonObject();
			meta.addProperty("kind", conversionType);
			meta.addProperty("persistableReference", fromCrs);
			meta.add("propertyNames", propertyNames);

			JsonArray metaBlocks = new JsonArray();
			metaBlocks.add(meta);

			JsonObject record = new JsonObject();
			record.addProperty("id", id + i);
			record.addProperty("kind", kind);
			record.add("acl", acl);
			record.add("legal", legal);
			record.add("data", data);
			record.add("meta", metaBlocks);

			records.add(record);
		}

		return records.toString();
	}
}