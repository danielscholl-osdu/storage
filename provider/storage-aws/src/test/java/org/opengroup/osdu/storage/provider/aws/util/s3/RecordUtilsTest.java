// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opengroup.osdu.storage.provider.aws.util.s3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.springframework.test.util.ReflectionTestUtils;


import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@ExtendWith(MockitoExtension.class)
class RecordUtilsTest {
    
    @InjectMocks
    private RecordsUtil recordsUtil;

    @Mock
    private S3RecordClient s3RecordClient;

    @Mock
    private DpsHeaders headers;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private RecordMetadata recordMetadata;

    private final WorkerThreadPool threadPool = new WorkerThreadPool(10);
    private String dataPartition = "dummyPartitionName";

    @BeforeEach
    void setuUp() {
        openMocks(this);
        ReflectionTestUtils.setField(recordsUtil, "threadPool", threadPool);
        when(headers.getPartitionIdWithFallbackToAccountId()).thenReturn(dataPartition);
    }

    @Test
    void testGetRecordsValuesById() {
        Map<String, String> objects = new HashMap<>();
        objects.put("record1", "version1");
        objects.put("record2", "version2");
        when(s3RecordClient.getRecord(anyString(), anyString())).thenAnswer(invocation -> {
        String recordId = invocation.getArgument(0);
        String dataPartitionId = invocation.getArgument(1);
        // Here, return a string that simulates what the real s3RecordClient would return
        return "Record: " + recordId + ", partition: " + dataPartitionId;
    });
        Map<String, String> result = recordsUtil.getRecordsValuesById(objects);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Record: version1, partition: dummyPartitionName", result.get("record1"));
        assertEquals("Record: version2, partition: dummyPartitionName", result.get("record2"));    

    }

    @Test
    void testGetRecordsValuesById_throwsException() {
        Map<String, String> objects = new HashMap<>();
        objects.put("record1", "version1");
        objects.put("record2", "version2");
        when(s3RecordClient.getRecord(anyString(), anyString())).thenThrow(new RuntimeException("test exception"));
        assertThrows(AppException.class, () -> recordsUtil.getRecordsValuesById(objects));
    }

    @Test
    void testGetRecordsValuesByIdWithMetadata() {
        AtomicReference<Map<String, String>> map = new AtomicReference<>();
        Collection<RecordMetadata> recordMetadatas = new ArrayList<>();
        recordMetadatas.add(recordMetadata);
        when(s3RecordClient.getRecord(eq(recordMetadata), any(AtomicReference.class), eq(dataPartition)))
        .thenAnswer(invocation -> {
            AtomicReference<Map<String, String>> mapArg = invocation.getArgument(1);
            mapArg.get().put(recordMetadata.getId(), "dummyRecord");
            return true;
        });


        Map<String,String> result = recordsUtil.getRecordsValuesById(recordMetadatas);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testGetRecordsValuesByIdWithMetadata_throwsException() {
        AtomicReference<Map<String, String>> map = new AtomicReference<>();
        Collection<RecordMetadata> recordMetadatas = new ArrayList<>();
        recordMetadatas.add(recordMetadata);
        when(s3RecordClient.getRecord(recordMetadata, map, dataPartition)).thenThrow(new RuntimeException("test exception"));
        assertThrows(AppException.class, () -> recordsUtil.getRecordsValuesById(recordMetadatas));
    }
    
}
