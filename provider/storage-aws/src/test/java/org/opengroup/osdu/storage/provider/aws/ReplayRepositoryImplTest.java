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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.ReplayMetadataItem;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReplayRepositoryImplTest {

    @Mock
    private DynamoDBMapper dynamoDBMapper;

    @Mock
    private AmazonDynamoDB dynamoDBClient;

    @Mock
    private JaxRsDpsLog logger;
    
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReplayRepositoryImpl replayRepository;

    private final String replayId = "test-replay-id";
    private final String kind = "test-kind";
    private final String partitionId = "test-partition";

    @Before
    public void setup() {
        ReflectionTestUtils.setField(replayRepository, "replayTableName", "ReplayStatus");
    }

    @Test
    public void testGetReplayStatusByKindAndReplayId() {
        // Setup
        ReplayMetadataItem item = createTestItem(kind, replayId);
        when(dynamoDBMapper.load(ReplayMetadataItem.class, kind, replayId)).thenReturn(item);

        // Execute
        ReplayMetaDataDTO result = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);

        // Verify
        assertNotNull(result);
        assertEquals(kind, result.getId());
        assertEquals(replayId, result.getReplayId());
    }

    private ReplayMetadataItem createTestItem(String id, String replayId) {
        ReplayMetadataItem item = new ReplayMetadataItem();
        item.setId(id);
        item.setReplayId(replayId);
        item.setKind(id.equals("overall") ? null : id);
        item.setOperation("replay");
        item.setTotalRecords(100L);
        item.setProcessedRecords(50L);
        item.setState("IN_PROGRESS");
        item.setStartedAt(new Date());
        item.setElapsedTime("00:05:30");
        item.setDataPartitionId(partitionId);
        return item;
    }
}
