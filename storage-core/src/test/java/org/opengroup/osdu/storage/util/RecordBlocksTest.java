package org.opengroup.osdu.storage.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordAncestry;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;

@RunWith(MockitoJUnitRunner.class)
public class RecordBlocksTest {

    @Mock
    private ICloudStorage cloudStorage;

    @InjectMocks
    private RecordBlocks recordBlocks;
    private static final String RECORD_ID1 = "tenant1:kind:record1";
    private static final String RECORD_ID2 = "tenant1:crazy:record2";
    private static final String KIND_1 = "tenant1:test:kind:1.0.0";
    private static final String KIND_2 = "tenant1:test:crazy:2.0.2";
    private static final String USER = "testuser@gmail.com";
    private static final String NEW_USER = "newuser@gmail.com";
    private static final String[] VALID_ACL = new String[]{"data.email1@tenant1.gmail.com", "data.test@tenant1.gmail.com"};

    private Record record1;
    private Record record2;

    private List<Record> records;
    private Acl acl = new Acl();

    @Before
    public void setup() {
        List<String> userHeaders = new ArrayList<>();
        userHeaders.add(USER);

        Legal legal = new Legal();
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));

        Groups groups = new Groups();
        List<GroupInfo> groupsInfo = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setEmail("test.group@mydomain.com");
        groupsInfo.add(groupInfo);
        groups.setGroups(groupsInfo);

        this.record1 = new Record();
        this.record1.setKind(KIND_1);
        this.record1.setId(RECORD_ID1);
        this.record1.setLegal(legal);
        // set up empty ancestry for record1
        RecordAncestry ancestry = new RecordAncestry();
        this.record1.setAncestry(ancestry);

        this.record2 = new Record();
        this.record2.setKind(KIND_2);
        this.record2.setId(RECORD_ID2);
        this.record2.setLegal(legal);

        this.records = new ArrayList<>();
        this.records.add(this.record1);
        this.records.add(this.record2);

        this.record1.setAcl(this.acl);
        this.record2.setAcl(this.acl);

    }


    @Test
    public void populateRecordBlocksMetadataEmptyWhenNoChange() {
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        RecordData recordData = new RecordData();
        recordData.setData(data);
        this.record1.setData(data);
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        existingRecordMetadata.setTags(tags);

        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setTags(tags);
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(recordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(recordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        Mockito.when(cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(this.record1));
        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess);
        assertEquals("", recordsToProcess.get(0).getRecordBlocks());
    }


    @Test
    public void populateRecordBlocksWhenChangeInDataOnly() {
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(data);
        this.record1.setData(data);
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        existingRecordMetadata.setTags(tags);
        existingRecordMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));

        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "USA");
        newData.put("state", "TX");
        newData.put("some key", "some value");
        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setTags(tags);
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(newRecordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        Mockito.when(cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(this.record1));
        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess);
        assertEquals("data", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenChangeInMetaDataOnly() {
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(data);
        this.record1.setData(data);
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        existingRecordMetadata.setTags(tags);
        existingRecordMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));

        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "USA");
        newData.put("state", "TX");

        Map<String, String> updatedTags = new HashMap<>();
        updatedTags.put("tag1", "new value1");
        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setTags(updatedTags);
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(newRecordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        Mockito.when(cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(this.record1));
        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess);
        assertEquals("metadata", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenNewMetaFieldAdded() {
        // meta added
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(data);
        this.record1.setData(data);
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));

        existingRecordMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));

        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "USA");
        newData.put("state", "TX");

        Map<String, String> newMeta = new HashMap<>();
        newMeta.put("some meta key", "some meta value");
        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);
        newRecordData.setMeta(new Map[]{newMeta});
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(newRecordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        Mockito.when(cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(this.record1));
        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess);
        assertEquals("metadata+", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenNewMetaFieldRemoved() {
        // meta Removed
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");
        Map<String, String> existingMeta = new HashMap<>();
        existingMeta.put("some meta key", "some meta value");

        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(data);
        existingRecordData.setMeta(new Map[]{existingMeta});
//        this.record1.setData(data);
//        this.record1.setMeta(new Map[]{existingMeta});
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        existingRecordMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));

        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "USA");
        newData.put("state", "TX");


        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(newRecordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);


        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess);
        assertEquals("metadata-", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenChangeInBothDataAndMetadata() {
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(data);
        this.record1.setData(data);
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        existingRecordMetadata.setTags(tags);
        existingRecordMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));

        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "USA");
        newData.put("state", "TX2");

        Map<String, String> updatedTags = new HashMap<>();
        updatedTags.put("tag1", "new value1");
        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setTags(updatedTags);
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(newRecordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        Mockito.when(cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(this.record1));
        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess);
        assertEquals("data metadata", recordsToProcess.get(0).getRecordBlocks());
    }

}
