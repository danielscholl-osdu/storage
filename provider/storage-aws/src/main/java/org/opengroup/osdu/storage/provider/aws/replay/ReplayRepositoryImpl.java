/*
 * Copyright © Amazon Web Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.replay;

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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
        List<AwsReplayMetaDataDTO> awsDtos = getAwsReplayStatusByReplayId(replayId);
        // Convert to List<ReplayMetaDataDTO> to satisfy interface
        return new ArrayList<>(awsDtos);
    }
    
    /**
     * Retrieves all replay metadata items for a given replay ID as AWS DTOs.
     *
     * @param replayId The unique identifier for the replay operation
     * @return A list of AwsReplayMetaDataDTO objects
     */
    public List<AwsReplayMetaDataDTO> getAwsReplayStatusByReplayId(String replayId) {
        DynamoDBQueryHelperV2 queryHelper = getReplayStatusQueryHelper();

        try {
            // Create a query object with replayId as the hash key for the GSI
            ReplayMetadataItem queryObject = new ReplayMetadataItem();
            queryObject.setReplayId(replayId);

            // Use queryByGSI to query the GSI directly
            QueryPageResult<ReplayMetadataItem> queryPageResult = queryHelper.queryByGSI(
                    ReplayMetadataItem.class,
                    queryObject,
                    1000,  // Use a reasonable page size
                    null); // Start with no cursor
            
            return queryPageResult.results.stream()
                    .map(this::convertToAwsDTO)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
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
        
        // Use the kind as the hash key and replayId as the range key
        ReplayMetadataItem item = queryHelper.loadByPrimaryKey(ReplayMetadataItem.class, kind, replayId);
        
        return item != null ? convertToDTO(item) : null;
    }
    
    /**
     * Gets the AWS-specific replay metadata DTO for a given kind and replay ID.
     * This method is similar to getReplayStatusByKindAndReplayId but returns the AWS-specific DTO
     * that includes the lastCursor and lastUpdatedAt fields.
     *
     * @param kind The kind of records being replayed
     * @param replayId The unique identifier for the replay operation
     * @return The AwsReplayMetaDataDTO object, or null if not found
     */
    public AwsReplayMetaDataDTO getAwsReplayStatusByKindAndReplayId(String kind, String replayId) {
        DynamoDBQueryHelperV2 queryHelper = getReplayStatusQueryHelper();
        
        // Use the kind as the hash key and replayId as the range key
        ReplayMetadataItem item = queryHelper.loadByPrimaryKey(ReplayMetadataItem.class, kind, replayId);
        
        return item != null ? convertToAwsDTO(item) : null;
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
     * Saves an AWS-specific replay metadata item to DynamoDB.
     *
     * @param awsReplayMetaData The AwsReplayMetaDataDTO to save
     * @return The saved AwsReplayMetaDataDTO
     */
    public AwsReplayMetaDataDTO saveAwsReplayMetaData(AwsReplayMetaDataDTO awsReplayMetaData) {
        DynamoDBQueryHelperV2 queryHelper = getReplayStatusQueryHelper();
        
        ReplayMetadataItem item = convertAwsDtoToItem(awsReplayMetaData);
        queryHelper.save(item);
        
        return convertToAwsDTO(item);
    }
    
    /**
     * Updates the lastCursor and lastUpdatedAt fields for a replay metadata item.
     *
     * @param kind The kind of records being replayed
     * @param replayId The unique identifier for the replay operation
     * @param cursor The current cursor position
     * @return The updated AwsReplayMetaDataDTO
     */
    public AwsReplayMetaDataDTO updateCursor(String kind, String replayId, String cursor) {
        AwsReplayMetaDataDTO awsDto = getAwsReplayStatusByKindAndReplayId(kind, replayId);
        if (awsDto != null) {
            awsDto.setLastCursor(cursor);
            awsDto.setLastUpdatedAt(new Date());
            return saveAwsReplayMetaData(awsDto);
        }
        return null;
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
        
        return dto;
    }
    
    /**
     * Converts a ReplayMetadataItem to an AWS-specific AwsReplayMetaDataDTO.
     *
     * @param item The ReplayMetadataItem to convert
     * @return The converted AwsReplayMetaDataDTO
     */
    private AwsReplayMetaDataDTO convertToAwsDTO(ReplayMetadataItem item) {
        // First convert to standard DTO
        ReplayMetaDataDTO baseDto = convertToDTO(item);
        
        // Then convert to AWS-specific DTO and add AWS-specific fields
        AwsReplayMetaDataDTO awsDto = AwsReplayMetaDataDTO.fromReplayMetaDataDTO(baseDto);
        awsDto.setLastCursor(item.getLastCursor());
        awsDto.setLastUpdatedAt(item.getLastUpdatedAt());
        
        return awsDto;
    }

    /**
     * Converts a ReplayMetaDataDTO to a ReplayMetadataItem.
     *
     * @param dto The ReplayMetaDataDTO to convert
     * @return The converted ReplayMetadataItem
     */
    private ReplayMetadataItem convertToItem(ReplayMetaDataDTO dto) {
        ReplayMetadataItem item = new ReplayMetadataItem();
        
        // Use the kind as the hash key (id) if it's not already set
        if (dto.getId() == null || dto.getId().isEmpty()) {
            item.setId(dto.getKind());
        } else {
            item.setId(dto.getId());
        }
        
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
        
        // If this is an AWS-specific DTO, set the AWS-specific fields
        if (dto instanceof AwsReplayMetaDataDTO awsDto) {
            item.setLastCursor(awsDto.getLastCursor());
            item.setLastUpdatedAt(awsDto.getLastUpdatedAt());
        }
        
        return item;
    }
    
    /**
     * Converts an AwsReplayMetaDataDTO to a ReplayMetadataItem.
     *
     * @param awsDto The AwsReplayMetaDataDTO to convert
     * @return The converted ReplayMetadataItem
     */
    private ReplayMetadataItem convertAwsDtoToItem(AwsReplayMetaDataDTO awsDto) {
        ReplayMetadataItem item = convertToItem(awsDto);
        item.setLastCursor(awsDto.getLastCursor());
        item.setLastUpdatedAt(awsDto.getLastUpdatedAt());
        return item;
    }
}
