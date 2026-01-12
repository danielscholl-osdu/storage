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

package org.opengroup.osdu.storage.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordAncestry;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class JsonPatchUtilTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldApplyPatch_toRecord() throws Exception {
        Record record = getRecord();
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer3\"}, {\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\" : \"world\"}}]"));
        Record patchedRecord = JsonPatchUtil.applyPatch(jsonPatch, Record.class, record);
        assertTrue(Arrays.stream(patchedRecord.getAcl().getViewers()).anyMatch("viewer3"::equals));
        patchedRecord.getData().containsKey("Hello");
        assertEquals("world", patchedRecord.getData().get("Hello"));
    }

    @Test
    public void shouldApplyPatch_toRecordMetadata() throws Exception {
        RecordMetadata recordMetadata = getRecordMetadata();
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer3\"}]"));
        RecordMetadata patchedRecordMetadata = JsonPatchUtil.applyPatch(jsonPatch, RecordMetadata.class, recordMetadata);
        assertTrue(Arrays.stream(patchedRecordMetadata.getAcl().getViewers()).anyMatch("viewer3"::equals));
    }

    @Test
    public void shouldReturnTrue_when_dataIsBeingPatched() throws Exception {
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer3\"}, {\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\" : \"world\"}}]"));
        assertTrue(JsonPatchUtil.isDataOrMetaBeingUpdated(jsonPatch));
    }

    @Test
    public void shouldReturnTrue_when_metaIsBeingPatched() throws Exception {
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer3\"}, {\"op\":\"add\", \"path\":\"/meta\", \"value\":[{\"Hello\" : \"world\"}]}]"));
        assertTrue(JsonPatchUtil.isDataOrMetaBeingUpdated(jsonPatch));
    }

    @Test
    public void shouldReturnFalse_when_neitherDataOrMetaIsBeingPatched() throws Exception {
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer3\"}]"));
        assertFalse(JsonPatchUtil.isDataOrMetaBeingUpdated(jsonPatch));
    }

    @Test
    public void shouldGetJsonPatchForRecord_with_duplicateHandling() throws Exception {
        RecordMetadata recordMetadata = getRecordMetadata();
        JsonPatch jsonPatchDuplicateAcl = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer1\"}]"));
        JsonPatch jsonPatchNewAcl = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer3\"}]"));
        JsonPatch jsonPatchNewAndDuplicate = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer3\"}]"));
        JsonPatch resultPatchEmpty = JsonPatchUtil.getJsonPatchForRecord(recordMetadata, jsonPatchDuplicateAcl);
        assertTrue(resultPatchEmpty.toString().equals("[]"));
        JsonPatch resultPatchSame = JsonPatchUtil.getJsonPatchForRecord(recordMetadata, jsonPatchNewAcl);
        assertEquals(jsonPatchNewAcl.toString(), resultPatchSame.toString());
        JsonPatch resultPatchRemovedDuplicate = JsonPatchUtil.getJsonPatchForRecord(recordMetadata, jsonPatchNewAndDuplicate);
        assertEquals(resultPatchRemovedDuplicate.toString(), jsonPatchNewAcl.toString());
    }

    @Test
    public void shouldReturnTrue_when_emptyViewersAclRecordMetadata() {
        RecordMetadata recordMetadata = getRecordMetadata();
        recordMetadata.getAcl().setViewers(new String[0]);
        assertTrue(JsonPatchUtil.isEmptyAclOrLegal(recordMetadata));
    }

    @Test
    public void shouldReturnTrue_when_emptyOwnersAclRecordMetadata() {
        RecordMetadata recordMetadata = getRecordMetadata();
        recordMetadata.getAcl().setOwners(new String[0]);
        assertTrue(JsonPatchUtil.isEmptyAclOrLegal(recordMetadata));
    }

    @Test
    public void shouldReturnTrue_when_emptyLegalRecordMetadata() {
        RecordMetadata recordMetadata = getRecordMetadata();
        recordMetadata.getLegal().setLegaltags(new HashSet<>());
        assertTrue(JsonPatchUtil.isEmptyAclOrLegal(recordMetadata));
    }

    @Test
    public void shouldReturnFalse_when_nonEmptyAclAndLegalRecordMetadata() {
        RecordMetadata recordMetadata = getRecordMetadata();
        assertFalse(JsonPatchUtil.isEmptyAclOrLegal(recordMetadata));
    }

    @Test
    public void shouldReturnTrue_when_emptyViewersAclRecord() {
        Record record = getRecord();
        record.getAcl().setViewers(new String[0]);
        assertTrue(JsonPatchUtil.isEmptyAclOrLegal(record));
    }

    @Test
    public void shouldReturnTrue_when_emptyOwnersAclRecord() {
        Record record = getRecord();
        record.getAcl().setOwners(new String[0]);
        assertTrue(JsonPatchUtil.isEmptyAclOrLegal(record));
    }

    @Test
    public void shouldReturnTrue_when_emptyLegalRecord() {
        Record record = getRecord();
        record.getLegal().setLegaltags(new HashSet<>());
        assertTrue(JsonPatchUtil.isEmptyAclOrLegal(record));
    }

    @Test
    public void shouldReturnFalse_when_nonEmptyAclAndLegalRecord() {
        Record record = getRecord();
        assertFalse(JsonPatchUtil.isEmptyAclOrLegal(record));
    }

    @Test
    public void shouldReturnTrue_when_kindIsUpdated() throws Exception {
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer3\"}, {\"op\":\"replace\", \"path\":\"/kind\", \"value\":\"newKind\"}]"));
        assertTrue(JsonPatchUtil.isKindBeingUpdated(jsonPatch));
    }

    @Test
    public void shouldReturnFalse_when_kindIsNotUpdated() throws Exception {
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer3\"}, {\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\" : \"world\"}}]"));
        assertFalse(JsonPatchUtil.isKindBeingUpdated(jsonPatch));
    }

    @Test
    public void shouldReturnKindFromJsonPatch() throws Exception {
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"viewer3\"}, {\"op\":\"replace\", \"path\":\"/kind\", \"value\":\"newKind\"}]"));
        assertEquals("newKind", JsonPatchUtil.getNewKindFromPatchInput(jsonPatch));
    }

    private Record getRecord() {
        Record record = new Record();
        record.setLegal(getLegal());
        record.setData(getData());
        record.setVersion(1L);
        record.setId("record1");
        record.setAcl(getAcl());
        record.setKind("kind1");
        record.setTags(getTags());
        record.setAncestry(getAncestry());
        return record;
    }

    private RecordMetadata getRecordMetadata() {
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setAcl(getAcl());
        recordMetadata.setLegal(getLegal());
        recordMetadata.setAncestry(getAncestry());
        recordMetadata.setTags(getTags());
        recordMetadata.setModifyTime(System.currentTimeMillis());
        recordMetadata.setModifyUser("user1");
        recordMetadata.setKind("kind1");
        recordMetadata.setPreviousVersionKind("previousKind");
        recordMetadata.setId("record1");
        return recordMetadata;
    }

    private Acl getAcl() {
        Acl acl = new Acl();
        acl.setViewers(new String[]{"viewer1", "viewer2"});
        acl.setOwners(new String[]{"owner1", "owner2"});
        return acl;
    }

    private Legal getLegal() {
        Legal legal = new Legal();
        Set<String> legaltags = new HashSet<>();
        legaltags.add("legaltag1");
        legal.setLegaltags(legaltags);
        return legal;
    }

    private Map getData() {
        Map<String, Object> data = new HashMap<>();
        data.put("dataKey", "dataValue");
        return data;
    }

    private Map getTags() {
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        tags.put("tag2", "value2");
        return tags;
    }

    private RecordAncestry getAncestry() {
        RecordAncestry ancestry = new RecordAncestry();
        Set<String> parents = new HashSet<>();
        parents.add("parent1");
        ancestry.setParents(parents);
        return ancestry;
    }
}
