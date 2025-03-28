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
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.ReplayMetadataItem;
import org.opengroup.osdu.storage.service.replay.IReplayRepository;
import org.opengroup.osdu.storage.service.replay.ReplayMetaDataDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AWS implementation of the IReplayRepository interface.
 * This class handles the storage and retrieval of replay metadata in DynamoDB.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayRepositoryImpl implements IReplayRepository {
    
    private final DynamoDBMapper dynamoDBMapper;
    private final AmazonDynamoDB dynamoDBClient;
    
    @Autowired
    private DpsHeaders headers;
    
    @Value("${aws.dynamodb.replay-table-name}")
    private String replayTableName;
    
    @Autowired
    public ReplayRepositoryImpl(DynamoDBMapper dynamoDBMapper, AmazonDynamoDB dynamoDBClient) {
        this.dynamoDBMapper = dynamoDBMapper;
        this.dynamoDBClient = dynamoDBClient;
    }
    
    /**
     * Retrieves all replay metadata items for a given replay ID.
     *
     * @param replayId The unique identifier for the replay operation
     * @return A list of ReplayMetaDataDTO objects
     */
    @Override
    public List<ReplayMetaDataDTO> getReplayStatusByReplayId(String replayId) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":replayId", new AttributeValue().withS(replayId));
        
        DynamoDBQueryExpression<ReplayMetadataItem> queryExpression = new DynamoDBQueryExpression<ReplayMetadataItem>()
                .withIndexName("replayId-index")
                .withConsistentRead(false)
                .withKeyConditionExpression("replayId = :replayId")
                .withExpressionAttributeValues(expressionAttributeValues);
        
        List<ReplayMetadataItem> items = dynamoDBMapper.query(ReplayMetadataItem.class, queryExpression);
        
        return items.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Retrieves a specific replay metadata item for a given kind and replay ID.
     *
     * @param kind The kind of records being replayed
     * @param replayId The unique identifier for the replay operation
     * @return The ReplayMetaDataDTO object, or null if not found
     */
    @Override
    public ReplayMetaDataDTO getReplayStatusByKindAndReplayId(String kind, String replayId) {
        ReplayMetadataItem key = new ReplayMetadataItem();
        key.setId(kind);
        key.setReplayId(replayId);
        
        ReplayMetadataItem item = dynamoDBMapper.load(ReplayMetadataItem.class, key.getId(), key.getReplayId());
        
        return item != null ? convertToDTO(item) : null;
    }
    
    /**
     * Saves a replay metadata item to DynamoDB.
     *
     * @param replayMetaData The ReplayMetaDataDTO to save
     * @return The saved ReplayMetaDataDTO
     */
    @Override
    public ReplayMetaDataDTO save(ReplayMetaDataDTO replayMetaData) {
        ReplayMetadataItem item = convertToItem(replayMetaData);
        item.setDataPartitionId(headers.getPartitionId());
        
        dynamoDBMapper.save(item);
        
        return convertToDTO(item);
    }
    
    /**
     * Converts a ReplayMetadataItem to a ReplayMetaDataDTO.
     *
     * @param item The ReplayMetadataItem to convert
     * @return The converted ReplayMetaDataDTO
     */
    private ReplayMetaDataDTO convertToDTO(ReplayMetadataItem item) {
        ReplayMetaDataDTO dto = new ReplayMetaDataDTO();
        dto.setId(item.getId());
        dto.setReplayId(item.getReplayId());
        dto.setKind(item.getKind());
        dto.setOperation(item.getOperation());
        dto.setTotalRecords(item.getTotalRecords());
        dto.setProcessedRecords(item.getProcessedRecords());
        dto.setState(item.getState());
        dto.setStartedAt(item.getStartedAt());
        dto.setElapsedTime(item.getElapsedTime());
        dto.setFilter(item.getFilter());
        dto.setDataPartitionId(item.getDataPartitionId());
        
        return dto;
    }
    
    /**
     * Converts a ReplayMetaDataDTO to a ReplayMetadataItem.
     *
     * @param dto The ReplayMetaDataDTO to convert
     * @return The converted ReplayMetadataItem
     */
    private ReplayMetadataItem convertToItem(ReplayMetaDataDTO dto) {
        ReplayMetadataItem item = new ReplayMetadataItem();
        item.setId(dto.getId());
        item.setReplayId(dto.getReplayId());
        item.setKind(dto.getKind());
        item.setOperation(dto.getOperation());
        item.setTotalRecords(dto.getTotalRecords());
        item.setProcessedRecords(dto.getProcessedRecords());
        item.setState(dto.getState());
        item.setStartedAt(dto.getStartedAt());
        item.setElapsedTime(dto.getElapsedTime());
        item.setFilter(dto.getFilter());
        item.setDataPartitionId(dto.getDataPartitionId());
        
        return item;
    }
}
