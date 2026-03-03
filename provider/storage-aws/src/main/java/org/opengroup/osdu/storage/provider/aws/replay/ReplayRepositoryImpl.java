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
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.aws.v2.dynamodb.model.GsiQueryRequest;
import org.opengroup.osdu.core.aws.v2.dynamodb.model.QueryPageResult;
import org.opengroup.osdu.core.aws.v2.dynamodb.util.RequestBuilderUtil;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.ReplayMetadataItem;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.opengroup.osdu.storage.request.ReplayFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;

import java.util.*;

/**
 * AWS implementation of the IReplayRepository interface.
 * This class handles the storage and retrieval of replay metadata in DynamoDB.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayRepositoryImpl implements IReplayRepository {

    private static final String GSI_REPLAY_ID_INDEX_NAME = "ReplayIdIndex";
    private final DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;
    private final DpsHeaders headers;
    private final JaxRsDpsLog logger;
    private final ObjectMapper objectMapper;
    
    @Value("${aws.dynamodb.replayStatusTable.ssm.relativePath}")
    private String replayStatusTableParameterRelativePath;
    
    @Autowired
    public ReplayRepositoryImpl(DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory,
                               DpsHeaders headers,
                               JaxRsDpsLog logger,
                               ObjectMapper objectMapper) {
        this.dynamoDBQueryHelperFactory = dynamoDBQueryHelperFactory;
        this.headers = headers;
        this.logger = logger;
        this.objectMapper = objectMapper;
    }
    
    private DynamoDBQueryHelper<ReplayMetadataItem> getReplayStatusQueryHelper() {
        return dynamoDBQueryHelperFactory.createQueryHelper(headers, replayStatusTableParameterRelativePath, ReplayMetadataItem.class);
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
        DynamoDBQueryHelper<ReplayMetadataItem> queryHelper = getReplayStatusQueryHelper();

        try {
            // Create a query object with replayId as the hash key for the GSI
            ReplayMetadataItem queryObject = new ReplayMetadataItem();
            queryObject.setReplayId(replayId);

            GsiQueryRequest<ReplayMetadataItem> queryRequest =
                    RequestBuilderUtil.QueryRequestBuilder.forQuery(queryObject, GSI_REPLAY_ID_INDEX_NAME, ReplayMetadataItem.class)
                            .limit(1000) // Use a reasonable page size
                            .cursor(null) // Start with no cursor
                            .buildGsiRequest();
            // Use queryByGSI to query the GSI directly
            QueryPageResult<ReplayMetadataItem> queryPageResult = queryHelper.queryByGSI(queryRequest, true);
            
            return queryPageResult.getItems().stream()
                    .map(this::convertToAwsDTO)
                    .toList();
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
        DynamoDBQueryHelper<ReplayMetadataItem> queryHelper = getReplayStatusQueryHelper();
        
        // Use the kind as the hash key and replayId as the range key
        Optional<ReplayMetadataItem> itemOptional = queryHelper.getItem(kind, replayId);
        
        return itemOptional.isPresent()  ? convertToDTO(itemOptional.get()) : null;
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
        DynamoDBQueryHelper<ReplayMetadataItem> queryHelper = getReplayStatusQueryHelper();
        
        // Use the kind as the hash key and replayId as the range key
        Optional<ReplayMetadataItem> itemOptional = queryHelper.getItem(kind, replayId);
        
        return itemOptional.isPresent() ? convertToAwsDTO(itemOptional.get()) : null;
    }
    
    /**
     * Batch retrieves AWS-specific replay metadata DTOs for multiple kinds and a single replay ID.
     * This method uses DynamoDB's batch load functionality to efficiently retrieve multiple items
     * in a single API call.
     *
     * @param kinds List of kinds to retrieve metadata for
     * @param replayId The unique identifier for the replay operation
     * @return A list of AwsReplayMetaDataDTO objects for the given kinds and replay ID
     */
    public List<AwsReplayMetaDataDTO> batchGetAwsReplayStatusByKindsAndReplayId(List<String> kinds, String replayId) {
        if (kinds == null || kinds.isEmpty()) {
            logger.info("No kinds provided for batch retrieval");
            return List.of();
        }
        
        DynamoDBQueryHelper<ReplayMetadataItem> queryHelper = getReplayStatusQueryHelper();
        
        try {
            // Use the new batchLoadByCompositeKey method to efficiently retrieve all items in a single API call
            // This handles the composite key (kind as hash key + replayId as range key)
            Set<String> kindSet = new HashSet<>(kinds);
            List<ReplayMetadataItem> items = queryHelper.batchLoadByCompositePrimaryKey(kindSet, replayId);

            // Convert items to DTOs
            return items.stream()
                    .map(this::convertToAwsDTO)
                    .toList();
        } catch (Exception e) {
            logger.error("Error during batch retrieval of replay metadata: " + e.getMessage(), e);
            
            // Fall back to individual retrievals if batch fails
            logger.info("Falling back to individual retrievals");
            return kinds.stream()
                    .map(kind -> getAwsReplayStatusByKindAndReplayId(kind, replayId))
                    .filter(Objects::nonNull)
                    .toList();
        }
    }
    
    /**
     * Saves a replay metadata item to DynamoDB.
     *
     * @param replayMetaData The ReplayMetaDataDTO to save
     * @return The saved ReplayMetaDataDTO
     */
    @Override
    public ReplayMetaDataDTO save(ReplayMetaDataDTO replayMetaData) {
        DynamoDBQueryHelper<ReplayMetadataItem> queryHelper = getReplayStatusQueryHelper();
        
        ReplayMetadataItem item = convertToItem(replayMetaData);
        queryHelper.putItem(item);
        
        return convertToDTO(item);
    }


    /**
     * Saves an AWS-specific replay metadata item to DynamoDB.
     *
     * @param awsReplayMetaData The AwsReplayMetaDataDTO to save
     * @return The saved AwsReplayMetaDataDTO
     */
    public AwsReplayMetaDataDTO saveAwsReplayMetaData(AwsReplayMetaDataDTO awsReplayMetaData) {
        DynamoDBQueryHelper<ReplayMetadataItem> queryHelper = getReplayStatusQueryHelper();

        ReplayMetadataItem item = convertAwsDtoToItem(awsReplayMetaData);
        queryHelper.putItem(item);

        return convertToAwsDTO(item);
    }

    /**
     * Saves a batch of AWS-specific replay metadata items to DynamoDB.
     * This method uses the DynamoDB batch write functionality to reduce the number of API calls.
     *
     * @param awsReplayMetaDataList The list of AwsReplayMetaDataDTO objects to save
     * @return A list of any failed batch operations
     */
    public List<ReplayMetadataItem> batchSaveAwsReplayMetaData(List<AwsReplayMetaDataDTO> awsReplayMetaDataList) {
        if (awsReplayMetaDataList == null || awsReplayMetaDataList.isEmpty()) {
            logger.info("No replay metadata items to save in batch");
            return List.of();
        }
        
        DynamoDBQueryHelper<ReplayMetadataItem> queryHelper = getReplayStatusQueryHelper();
        
        // Convert all DTOs to DynamoDB items
        List<ReplayMetadataItem> items = awsReplayMetaDataList.stream()
                .map(this::convertAwsDtoToItem)
                .toList();

        try {
            // Use the batch save functionality
            BatchWriteResult result = queryHelper.batchSave(items);
            List<ReplayMetadataItem> failedBatches = result.unprocessedPutItemsForTable(queryHelper.getTable());
            if (!failedBatches.isEmpty()) {
                logger.error(String.format("Failed to save %d batches during batch save operation", failedBatches.size()));
            }
            
            return failedBatches;
        } catch (Exception e) {
            logger.error("Error during batch save of replay metadata: " + e.getMessage(), e);
            throw e;
        }
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

