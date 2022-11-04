package org.opengroup.osdu.storage.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RecordBlocks {

    private ICloudStorage cloudStorage;

    @Autowired
    public RecordBlocks(ICloudStorage cloudStorage) {
        this.cloudStorage = cloudStorage;
    }

    private static final String HASH_KEY_DATA = "data";
    private static final String HASH_KEY_META = "meta";
    private ObjectMapper objectMapper = new ObjectMapper();

    public void populateRecordBlocksMetadata(Map<String, RecordMetadata> existingRecords, List<RecordProcessing> recordsToProcess) {

        for (RecordProcessing x : recordsToProcess) {
            if (x.getOperationType().equals(OperationType.update)) {
                String recordBlocksUpdate = "";
                String id = x.getRecordMetadata().getId();
                RecordMetadata previousRecordMetadata = existingRecords.get(id);
                Map<String, Integer> previousMetadataCompare = populateHashes(previousRecordMetadata);
                Map<String, Integer> currentMetadataCompare = populateHashes(x.getRecordMetadata());

                if (previousRecordMetadata.getHash() == null) {
                    //Fetch recordData from storage for hash and meta comparison

                    RecordData recordData = null;
                    try {
                        recordData = objectMapper.readValue(cloudStorage.read(previousRecordMetadata, previousRecordMetadata.getLatestVersion(), false), RecordData.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    Map<String, String> hash = hashForRecordData(recordData);
                    previousRecordMetadata.setHash(hash);
                }
                Map<String, String> previousHash = previousRecordMetadata.getHash();
                Map<String, String> currentHash = x.getRecordMetadata().getHash();

                if (!previousHash.getOrDefault(HASH_KEY_DATA, "").equals(currentHash.getOrDefault(HASH_KEY_DATA, ""))) {
                    recordBlocksUpdate = "data";
                }

                Optional.ofNullable(previousHash.get(HASH_KEY_META)).ifPresent(y -> previousMetadataCompare.put("meta", y.hashCode()));
                Optional.ofNullable(currentHash.get(HASH_KEY_META)).ifPresent(y -> currentMetadataCompare.put("meta", y.hashCode()));

                recordBlocksUpdate += getRecordBlocksUpdate(previousMetadataCompare, currentMetadataCompare);
                x.setRecordBlocks(recordBlocksUpdate.trim());

            }
        }
    }

    private static String getRecordBlocksUpdate(Map<String, Integer> previousMetadataCompare, Map<String, Integer> currentMetadataCompare) {
        String recordBlocksMetadata = "";
        MapDifference<String, Object> mapDifference = Maps.difference(previousMetadataCompare, currentMetadataCompare);

        if (!mapDifference.areEqual()) {
            recordBlocksMetadata += " metadata";
        }
        if (mapDifference.entriesOnlyOnRight().size() > 0) {
            // Some key added
            recordBlocksMetadata += "+";
        }
        if (mapDifference.entriesOnlyOnLeft().size() > 0) {
            // Some key removed
            recordBlocksMetadata += "-";
        }
        return recordBlocksMetadata;
    }

    private static Map<String, Integer> populateHashes(RecordMetadata recordMetadata) {
        Map<String, Integer> map = new HashMap<>();
        Optional.ofNullable(recordMetadata.getAcl()).ifPresent(y -> map.put("acl", y.hashCode()));
        Optional.ofNullable(recordMetadata.getLegal()).ifPresent(y -> map.put("legal", y.hashCode()));
        Optional.ofNullable(recordMetadata.getTags()).ifPresent(y -> map.put("tags", y.hashCode()));
        Optional.ofNullable(recordMetadata.getAncestry()).ifPresent(y -> map.put("ancestry", y.hashCode()));
        return map;
    }

    public Map<String, String> hashForRecordData(RecordData recordData) {
        Map<String, String> hash = new HashMap<>();
        hash.put(HASH_KEY_DATA, CrcHashGenerator.getHash(recordData.getData()));
        if (recordData.getMeta() != null) {
            hash.put(HASH_KEY_META, CrcHashGenerator.getHash(recordData.getMeta()));
        }
        return hash;
    }
}
