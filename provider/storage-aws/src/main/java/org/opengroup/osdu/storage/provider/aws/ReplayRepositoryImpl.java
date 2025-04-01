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
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.ReplayMetadataItem;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.opengroup.osdu.storage.request.ReplayFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
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
    
    private final DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;
    private final WorkerThreadPool workerThreadPool;
    private final DpsHeaders headers;
    private final JaxRsDpsLog logger;
    private final ObjectMapper objectMapper;
    
    @Value("${aws.dynamodb.replayStatusTable.ssm.relativePath}")
    private String replayStatusTableParameterRelativePath;
    
    @Autowired
    public ReplayRepositoryImpl(DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory,
                               WorkerThreadPool workerThreadPool,
                               DpsHeaders headers,
                               JaxRsDpsLog logger,
                               ObjectMapper objectMapper) {
        this.dynamoDBQueryHelperFactory = dynamoDBQueryHelperFactory;
        this.workerThreadPool = workerThreadPool;
        this.headers = headers;
        this.logger = logger;
        this.objectMapper = objectMapper;
    }
    
    private DynamoDBQueryHelperV2 getReplayStatusQueryHelper() {
        return dynamoDBQueryHelperFactory.getQueryHelperForPartition(headers, replayStatusTableParameterRelativePath,
                workerThreadPool.getClientConfiguration());
    }
    
    /**
     * Retrieves all replay metadata items for a given replay ID.
     *
     * @param replayId The unique identifier for the replay operation
     * @return A list of ReplayMetaDataDTO objects
     */
    @Override
    public List<ReplayMetaDataDTO> getReplayStatusByReplayId(String replayId) {
        DynamoDBQueryHelperV2 queryHelper = getReplayStatusQueryHelper();
        
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":replayId", new AttributeValue().withS(replayId));
        
        DynamoDBQueryExpression<ReplayMetadataItem> queryExpression = new DynamoDBQueryExpression<ReplayMetadataItem>()
                .withIndexName("replayId-index")
                .withConsistentRead(false)
                .withKeyConditionExpression("replayId = :replayId")
                .withExpressionAttributeValues(expressionAttributeValues);
        
        // Use queryPage instead of query since DynamoDBQueryHelperV2 doesn't have a direct query method
        try {
            QueryPageResult<ReplayMetadataItem> queryResult = queryHelper.queryPage(ReplayMetadataItem.class, null, 1000, null);
            List<ReplayMetadataItem> items = queryResult.results;
            
            return items.stream()
                    .filter(item -> replayId.equals(item.getReplayId()))
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (UnsupportedEncodingException e) {
            logger.error("Error querying replay status: " + e.getMessage(), e);
            return new ArrayList<>();
        }
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
        DynamoDBQueryHelperV2 queryHelper = getReplayStatusQueryHelper();
        
        ReplayMetadataItem item = queryHelper.loadByPrimaryKey(ReplayMetadataItem.class, kind, replayId);
        
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
        DynamoDBQueryHelperV2 queryHelper = getReplayStatusQueryHelper();
        
        ReplayMetadataItem item = convertToItem(replayMetaData);
        queryHelper.save(item);
        
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

        // Convert filter string to ReplayFilter object
        if (item.getFilter() != null) {
            try {
                dto.setFilter(objectMapper.readValue(item.getFilter(), ReplayFilter.class));
            } catch (JsonProcessingException e) {
                logger.error("Failed to deserialize filter", e);
                dto.setFilter(null);
            }
        }
        
        // Note: ReplayMetaDataDTO doesn't have a dataPartitionId field, so we don't set it
        
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
        
        // Convert ReplayFilter object to string
        if (dto.getFilter() != null) {
            try {
                item.setFilter(objectMapper.writeValueAsString(dto.getFilter()));
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize filter", e);
                item.setFilter(null);
            }
        }
        
        // Set the data partition ID from the headers
        item.setDataPartitionId(headers.getPartitionId());
        
        return item;
    }
}
