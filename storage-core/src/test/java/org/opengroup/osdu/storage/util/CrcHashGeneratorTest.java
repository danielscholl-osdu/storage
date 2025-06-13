package org.opengroup.osdu.storage.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.model.storage.RecordData;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CrcHashGeneratorTest {

    CrcHashGenerator crcHashGenerator = new CrcHashGenerator();
    @Test
    @DisplayName("Validate same Hash is generated for records with same value")
    void validateSameHashIsGenerated_For_RecordsWithSameValue() {
        Map<String, Object> map1 = new HashMap<>();
        map1.put("abc","def");
        map1.put("ghi","jkl");

        Map<String, Object> map2 = new HashMap<>();
        map2.put("abc","def");
        map2.put("ghi","jkl");

        RecordData record1 = new RecordData();
        record1.setData(map1);

        RecordData record2 = new RecordData();
        record2.setData(map2);
        assertEquals(crcHashGenerator.getHash(record1), crcHashGenerator.getHash(record2));
    }

    @Test
    @DisplayName("Validate different Hash is generated for records with different value")
    void validateDifferentHashIsGenerated_For_RecordsWithDifferentValue() {
        Map<String, Object> map1 = new HashMap<>();
        map1.put("abc","def");
        map1.put("ghi","jkl");

        Map<String, Object> map2 = new HashMap<>();
        map2.put("abc","defg");
        map2.put("ghi","jklm");

        RecordData record1 = new RecordData();
        record1.setData(map1);

        RecordData record2 = new RecordData();
        record2.setData(map2);
        assertNotEquals(crcHashGenerator.getHash(record1), crcHashGenerator.getHash(record2));
    }
}
