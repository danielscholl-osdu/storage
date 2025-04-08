// Copyright © Amazon Web Services
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

package org.opengroup.osdu.storage.provider.aws;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.ReplayMetadataItem;
import org.opengroup.osdu.storage.request.ReplayFilter;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReplayRepositoryImplTest {

    @Mock
    private DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Mock
    private DynamoDBQueryHelperV2 dynamoDBQueryHelper;

    @Mock
    private WorkerThreadPool workerThreadPool;

    @Mock
    private DpsHeaders headers;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReplayRepositoryImpl replayRepository;

    private static final String REPLAY_ID = "test-replay-id";
    private static final String KIND = "test-kind";
    private static final String PARTITION_ID = "test-partition";
    private static final String REPLAY_TABLE_PATH = "services/core/storage/ReplayStatusTable";

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(replayRepository, "replayStatusTableParameterRelativePath", REPLAY_TABLE_PATH);
        when(headers.getPartitionId()).thenReturn(PARTITION_ID);
        when(workerThreadPool.getClientConfiguration()).thenReturn(null);
        when(dynamoDBQueryHelperFactory.getQueryHelperForPartition(eq(headers), eq(REPLAY_TABLE_PATH), any())).thenReturn(dynamoDBQueryHelper);
    }

    @Test
    public void testGetReplayStatusByReplayId() throws UnsupportedEncodingException {
        // Prepare test data
        ReplayMetadataItem item1 = createReplayMetadataItem(KIND, REPLAY_ID);
        ReplayMetadataItem item2 = createReplayMetadataItem("another-kind", REPLAY_ID);
        List<ReplayMetadataItem> items = Arrays.asList(item1, item2);
        
        QueryPageResult<ReplayMetadataItem> queryResult = new QueryPageResult<>(null, items);
        
        // Mock behavior for queryByGSI
        when(dynamoDBQueryHelper.queryByGSI(
                eq(ReplayMetadataItem.class),
                any(ReplayMetadataItem.class),
                eq(1000),
                isNull())).thenReturn(queryResult);
        
        // Execute
        List<ReplayMetaDataDTO> result = replayRepository.getReplayStatusByReplayId(REPLAY_ID);
        
        // Verify
        assertEquals(2, result.size());
        verify(dynamoDBQueryHelper).queryByGSI(
                eq(ReplayMetadataItem.class),
                any(ReplayMetadataItem.class),
                eq(1000),
                isNull());
    }

    @Test
    public void testGetReplayStatusByReplayIdHandlesException() throws UnsupportedEncodingException {
        // Mock behavior to throw exception
        when(dynamoDBQueryHelper.queryByGSI(
                eq(ReplayMetadataItem.class),
                any(ReplayMetadataItem.class),
                eq(1000),
                isNull())).thenThrow(new UnsupportedEncodingException("Test exception"));
        
        // Execute
        List<ReplayMetaDataDTO> result = replayRepository.getReplayStatusByReplayId(REPLAY_ID);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(logger).error(contains("Error querying replay status"), any(UnsupportedEncodingException.class));
    }

    @Test
    public void testGetReplayStatusByKindAndReplayId() {
        // Prepare test data
        ReplayMetadataItem item = createReplayMetadataItem(KIND, REPLAY_ID);
        
        // Mock behavior
        when(dynamoDBQueryHelper.loadByPrimaryKey(ReplayMetadataItem.class, KIND, REPLAY_ID)).thenReturn(item);
        
        // Execute
        ReplayMetaDataDTO result = replayRepository.getReplayStatusByKindAndReplayId(KIND, REPLAY_ID);
        
        // Verify
        assertNotNull(result);
        assertEquals(KIND, result.getKind());
        assertEquals(REPLAY_ID, result.getReplayId());
        verify(dynamoDBQueryHelper).loadByPrimaryKey(ReplayMetadataItem.class, KIND, REPLAY_ID);
    }

    @Test
    public void testGetReplayStatusByKindAndReplayIdReturnsNullWhenNotFound() {
        // Mock behavior
        when(dynamoDBQueryHelper.loadByPrimaryKey(ReplayMetadataItem.class, KIND, REPLAY_ID)).thenReturn(null);
        
        // Execute
        ReplayMetaDataDTO result = replayRepository.getReplayStatusByKindAndReplayId(KIND, REPLAY_ID);
        
        // Verify
        assertNull(result);
        verify(dynamoDBQueryHelper).loadByPrimaryKey(ReplayMetadataItem.class, KIND, REPLAY_ID);
    }

    @Test
    public void testSave() {
        // Prepare test data
        ReplayMetaDataDTO dto = createReplayMetaDataDTO(KIND, REPLAY_ID);
        ReplayMetadataItem item = createReplayMetadataItem(KIND, REPLAY_ID);
        
        // Mock behavior
        doNothing().when(dynamoDBQueryHelper).save(any(ReplayMetadataItem.class));
        
        // Execute
        ReplayMetaDataDTO result = replayRepository.save(dto);
        
        // Verify
        assertNotNull(result);
        assertEquals(KIND, result.getKind());
        assertEquals(REPLAY_ID, result.getReplayId());
        verify(dynamoDBQueryHelper).save(any(ReplayMetadataItem.class));
    }

    @Test
    public void testConvertToItemHandlesFilterSerialization() throws JsonProcessingException {
        // Prepare test data
        ReplayMetaDataDTO dto = createReplayMetaDataDTO(KIND, REPLAY_ID);
        ReplayFilter filter = new ReplayFilter();
        filter.setKinds(Collections.singletonList(KIND));
        dto.setFilter(filter);
        
        String serializedFilter = "{\"kinds\":[\"" + KIND + "\"]}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(filter)).thenReturn(serializedFilter);
        
        // Execute - we need to use reflection to test private method
        ReplayMetadataItem result = (ReplayMetadataItem) ReflectionTestUtils.invokeMethod(
                replayRepository, 
                "convertToItem", 
                dto);
        
        // Verify
        assertNotNull(result);
        assertEquals(serializedFilter, result.getFilter());
        assertEquals(PARTITION_ID, result.getDataPartitionId());
        verify(objectMapper).writeValueAsString(filter);
    }

    @Test
    public void testConvertToDtoHandlesFilterDeserialization() throws JsonProcessingException {
        // Prepare test data
        ReplayMetadataItem item = createReplayMetadataItem(KIND, REPLAY_ID);
        String serializedFilter = "{\"kinds\":[\"" + KIND + "\"]}";
        item.setFilter(serializedFilter);
        
        ReplayFilter filter = new ReplayFilter();
        filter.setKinds(Collections.singletonList(KIND));
        
        // Mock behavior
        when(objectMapper.readValue(serializedFilter, ReplayFilter.class)).thenReturn(filter);
        
        // Execute - we need to use reflection to test private method
        ReplayMetaDataDTO result = (ReplayMetaDataDTO) ReflectionTestUtils.invokeMethod(
                replayRepository, 
                "convertToDTO", 
                item);
        
        // Verify
        assertNotNull(result);
        assertEquals(filter, result.getFilter());
        verify(objectMapper).readValue(serializedFilter, ReplayFilter.class);
    }

    private ReplayMetadataItem createReplayMetadataItem(String kind, String replayId) {
        ReplayMetadataItem item = new ReplayMetadataItem();
        item.setId(kind);
        item.setReplayId(replayId);
        item.setKind(kind);
        item.setOperation("replay");
        item.setTotalRecords(100L);
        item.setProcessedRecords(50L);
        item.setState("IN_PROGRESS");
        item.setStartedAt(new Date());
        item.setElapsedTime("00:10:00");
        item.setDataPartitionId(PARTITION_ID);
        return item;
    }

    private ReplayMetaDataDTO createReplayMetaDataDTO(String kind, String replayId) {
        ReplayMetaDataDTO dto = new ReplayMetaDataDTO();
        dto.setId(kind);
        dto.setReplayId(replayId);
        dto.setKind(kind);
        dto.setOperation("replay");
        dto.setTotalRecords(100L);
        dto.setProcessedRecords(50L);
        dto.setState("IN_PROGRESS");
        dto.setStartedAt(new Date());
        dto.setElapsedTime("00:10:00");
        return dto;
    }
}
