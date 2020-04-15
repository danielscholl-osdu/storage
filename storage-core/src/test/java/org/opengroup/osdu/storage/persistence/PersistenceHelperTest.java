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

package org.opengroup.osdu.storage.persistence;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordAncestry;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.storage.PersistenceHelper;

public class PersistenceHelperTest {

    @Test
    public void should_returnAnEmptyAttributeList_when_providingNullOrEmptyAttributesArray() {

        List<String> attributes = PersistenceHelper.getValidRecordAttributes(null);
        assertTrue(attributes.isEmpty());

        attributes = PersistenceHelper.getValidRecordAttributes(new String[] {});
        assertTrue(attributes.isEmpty());
    }

    @Test
    public void should_discardAttribute_when_attributeDoesNotStartWithPrefixData() {
        List<String> attributes = PersistenceHelper.getValidRecordAttributes(new String[] { "invalid.attribute" });
        assertTrue(attributes.isEmpty());
    }

    @Test
    public void should_returnValidAttributes_when_providingAttributeArrayWithDataPrefix() {
        List<String> attributes = PersistenceHelper
                .getValidRecordAttributes(new String[] { "data.age", "data.year", "invalid.attribute", "data.town" });

        assertEquals(3, attributes.size());
        assertArrayEquals(new String[] { "age", "year", "town" }, attributes.toArray(new String[attributes.size()]));
    }

    @Test
    public void should_returnWholeRecord_when_noAttributesAreProvided() {

        Acl acl = new Acl();
        acl.setViewers(new String[] { "viewers1", "viewers2" });
        acl.setOwners(new String[] { "owners1", "owners2" });

        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");
        data.put("city", "Houston");

        Record recordData = new Record();
        recordData.setId("ID1");
        recordData.setAcl(acl);
        recordData.setVersion(123L);
        recordData.setData(data);
        recordData.setKind("anyKind");

        JsonParser jsonParser = new JsonParser();
        Gson gson = new Gson();

        JsonElement recordJson = jsonParser.parse(gson.toJson(recordData));

        JsonElement result = PersistenceHelper.filterRecordDataFields(recordJson, new ArrayList<>());
        assertEquals(recordJson, result);

        result = PersistenceHelper.filterRecordDataFields(recordJson, null);
        assertEquals(recordJson, result);
    }

    @Test
    public void should_onlyReturnRecordProperties_when_providingValidDataAttributes() {

        List<String> attributes = new ArrayList<>();
        attributes.add("state");

        Acl acl = new Acl();
        acl.setViewers(new String[] { "viewers1", "viewers2" });
        acl.setOwners(new String[] { "owners1", "owners2" });

        Map<String, Object> originalData = new HashMap<>();
        originalData.put("country", "USA");
        originalData.put("state", "TX");
        originalData.put("city", "Houston");

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("state", "TX");

        Record originalRecord = new Record();
        originalRecord.setId("ID1");
        originalRecord.setAcl(acl);
        originalRecord.setVersion(123L);
        originalRecord.setData(originalData);
        originalRecord.setKind("anyKind");

        Record expectedRecord = new Record();
        expectedRecord.setId("ID1");
        expectedRecord.setAcl(acl);
        expectedRecord.setVersion(123L);
        expectedRecord.setData(expectedData);
        expectedRecord.setKind("anyKind");

        JsonParser jsonParser = new JsonParser();
        Gson gson = new Gson();

        JsonElement originalRecordJson = jsonParser.parse(gson.toJson(originalRecord));
        JsonElement expectedRecordJson = jsonParser.parse(gson.toJson(expectedRecord));

        JsonElement result = PersistenceHelper.filterRecordDataFields(originalRecordJson, attributes);
        assertEquals(expectedRecordJson, result);
    }

    @Test
    public void should_onlyReturnRecordProperties_when_providingValidNestedDataAttributes() {

        List<String> attributes = new ArrayList<>();
        attributes.add("address.state");

        Acl acl = new Acl();
        acl.setViewers(new String[] { "viewers1", "viewers2" });
        acl.setOwners(new String[] { "owners1", "owners2" });

        AddressData address = new AddressData("houston", "TX", "USA");

        Map<String, Object> originalData = new HashMap<>();
        originalData.put("address", address);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("address.state", "TX");

        Record originalRecord = new Record();
        originalRecord.setId("ID1");
        originalRecord.setAcl(acl);
        originalRecord.setVersion(123L);
        originalRecord.setData(originalData);
        originalRecord.setKind("anyKind");

        Record expectedRecord = new Record();
        expectedRecord.setId("ID1");
        expectedRecord.setAcl(acl);
        expectedRecord.setVersion(123L);
        expectedRecord.setData(expectedData);
        expectedRecord.setKind("anyKind");

        JsonParser jsonParser = new JsonParser();
        Gson gson = new Gson();

        JsonElement originalRecordJson = jsonParser.parse(gson.toJson(originalRecord));
        JsonElement expectedRecordJson = jsonParser.parse(gson.toJson(expectedRecord));

        JsonElement result = PersistenceHelper.filterRecordDataFields(originalRecordJson, attributes);
        assertEquals(expectedRecordJson, result);
    }

    @Test
    public void should_combineJsonFromStorageWithRecordFromDataStore_when_generatingStringCopyOfTheRecord() {

        JsonParser parser = new JsonParser();

        JsonElement element = parser.parse("{'anything':'anyvalue'}");

        Acl acl = new Acl();
        acl.setViewers(new String[] { "viewer1", "viewer2" });
        acl.setOwners(new String[] { "owner3", "owner4" });

        Legal legal = new Legal();
        legal.setLegaltags(Sets.newHashSet("legal1", "legal6"));
        legal.setOtherRelevantDataCountries(Sets.newHashSet("BRA", "FRA"));
        legal.setStatus(LegalCompliance.compliant);

        RecordAncestry ancestry = new RecordAncestry();
        ancestry.setParents(Sets.newHashSet("parentXYZ"));

        RecordMetadata metadata = new RecordMetadata();
        metadata.setId("ID12346");
        metadata.setKind("tenant1:test:kind:1.0.0");
        metadata.setAcl(acl);
        metadata.setLegal(legal);
        metadata.setAncestry(ancestry);

        String jsonString = PersistenceHelper.combineRecordMetaDataAndRecordData(element, metadata, 123L);

        JsonElement recordElement = parser.parse(jsonString);
        assertTrue(recordElement.isJsonObject());

        JsonObject json = recordElement.getAsJsonObject();
        assertEquals("anyvalue", json.get("anything").getAsString());
        assertEquals("ID12346", json.get("id").getAsString());
        assertEquals(123L, json.get("version").getAsLong());
        assertEquals("tenant1:test:kind:1.0.0", json.get("kind").getAsString());

        JsonElement aclElement = json.get("acl");
        assertTrue(aclElement.isJsonObject());

        JsonObject aclJson = aclElement.getAsJsonObject();
        assertEquals(2, aclJson.get("viewers").getAsJsonArray().size());
        assertEquals("viewer1", aclJson.get("viewers").getAsJsonArray().get(0).getAsString());
        assertEquals("viewer2", aclJson.get("viewers").getAsJsonArray().get(1).getAsString());
        assertEquals(2, aclJson.get("owners").getAsJsonArray().size());
        assertEquals("owner3", aclJson.get("owners").getAsJsonArray().get(0).getAsString());
        assertEquals("owner4", aclJson.get("owners").getAsJsonArray().get(1).getAsString());

        JsonElement legalElement = json.get("legal");
        assertTrue(legalElement.isJsonObject());

        JsonObject legalJson = legalElement.getAsJsonObject();
        assertEquals(2, legalJson.get("legaltags").getAsJsonArray().size());
        assertEquals("legal1", legalJson.get("legaltags").getAsJsonArray().get(0).getAsString());
        assertEquals("legal6", legalJson.get("legaltags").getAsJsonArray().get(1).getAsString());
        assertEquals(2, legalJson.get("otherRelevantDataCountries").getAsJsonArray().size());
        assertEquals("BRA", legalJson.get("otherRelevantDataCountries").getAsJsonArray().get(0).getAsString());
        assertEquals("FRA", legalJson.get("otherRelevantDataCountries").getAsJsonArray().get(1).getAsString());

        JsonElement ancestryElement = json.get("ancestry");
        assertTrue(ancestryElement.isJsonObject());

        JsonObject ancestryJson = ancestryElement.getAsJsonObject();
        assertEquals(1, ancestryJson.get("parents").getAsJsonArray().size());
        assertEquals("parentXYZ", ancestryJson.get("parents").getAsJsonArray().get(0).getAsString());
    }

    protected class AddressData {
        protected String city;
        protected String state;
        protected String country;

        protected AddressData(String city, String state, String country) {
            this.city = city;
            this.state = state;
            this.country = country;
        }

        public String getCity() {
            return this.city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState() {
            return this.state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getCountry() {
            return this.country;
        }

        public void setCountry(String country) {
            this.country = country;
        }
    }
}
