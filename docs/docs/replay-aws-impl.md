# AWS Replay Feature Implementation

This document outlines the implementation of the Replay feature in the AWS provider for OSDU Storage Service, based on the existing Azure implementation but adapted to use AWS-native messaging patterns with SNS and SQS.

## 1. Architecture Overview

The AWS implementation uses an SNS-SQS pattern for message publishing and consumption:

```
User Request → Storage API → SNS Topics → SQS Queues → Consumers
                    ↕
               DynamoDB (status tracking)
```

This pattern provides better decoupling between publishers and consumers, allowing multiple consumers to subscribe to the same messages if needed.

## 2. Core Components

### 2.1 DynamoDB Table for Replay Status

We've implemented a DynamoDB table to store replay status information:

```java
@Data
@DynamoDBTable(tableName = "ReplayStatus")
public class ReplayMetadataItem {
    @DynamoDBHashKey
    private String id;  // This is the kind
    
    @DynamoDBRangeKey
    private String replayId;
    
    private String kind;
    private String operation;
    private Long totalRecords;
    private Long processedRecords;
    private String state;
    private Date startedAt;
    private String elapsedTime;
    private String filter;  // JSON serialized filter
    private String dataPartitionId;
}
```

The table uses a composite key structure:
- Hash key (id): The kind being replayed
- Range key (replayId): The unique identifier for the replay operation

This structure allows efficient lookups by both kind and replayId, which is essential for tracking the status of individual kinds within a replay operation.

### 2.2 Repository Implementation

The `ReplayRepositoryImpl` class implements the `IReplayRepository` interface and handles all interactions with the DynamoDB table:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayRepositoryImpl implements IReplayRepository {
    // Implementation details...
    
    @Override
    public List<ReplayMetaDataDTO> getReplayStatusByReplayId(String replayId) {
        // Query DynamoDB for all items with the given replayId
    }
    
    @Override
    public ReplayMetaDataDTO getReplayStatusByKindAndReplayId(String kind, String replayId) {
        // Load a specific item by its composite key (kind + replayId)
    }
    
    @Override
    public ReplayMetaDataDTO save(ReplayMetaDataDTO replayMetaData) {
        // Save the replay metadata to DynamoDB
    }
}
```

### 2.3 Message Handling

#### 2.3.1 Publishing Messages with SNS

The `ReplayMessageHandler` class is responsible for publishing replay messages to SNS topics:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayMessageHandler {
    // Implementation details...
    
    public void sendReplayMessage(List<ReplayMessage> messages, String operation) {
        // Select appropriate topic based on operation
        String topicArn = getTopicArnForOperation(operation);
        
        // Publish each message to the SNS topic
        for (ReplayMessage message : messages) {
            // Serialize and publish the message
        }
    }
    
    private String getTopicArnForOperation(String operation) {
        // Return the appropriate SNS topic ARN based on the operation
    }
}
```

#### 2.3.2 Consuming Messages with SQS

The `ReplaySubscriptionMessageHandler` class polls SQS queues for replay messages:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplaySubscriptionMessageHandler {
    // Implementation details...
    
    @Scheduled(fixedDelayString = "${aws.sqs.polling-interval-ms:1000}")
    public void pollMessages() {
        // Poll the SQS queue for messages
        // Process each message with exponential backoff retry logic
    }
    
    private void processMessage(Message message) {
        // Extract the replay message from the SQS message
        // Process the message within a request context
    }
}
```

### 2.4 Message Processing

The `ReplayMessageProcessorAWSImpl` class handles the actual processing of replay messages:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayMessageProcessorAWSImpl {
    // Implementation details...
    
    public void processReplayMessage(ReplayMessage replayMessage) {
        // Update status to IN_PROGRESS
        // Get record IDs for the specified kind
        // Process records in batches
        // Update status to COMPLETED
    }
    
    public void processFailure(ReplayMessage replayMessage) {
        // Update status to FAILED
        // Log the failure
    }
}
```

### 2.5 Service Implementation

The `ReplayServiceAWSImpl` class extends the core `ReplayService` class and provides AWS-specific implementations:

```java
@Primary
@Service
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayServiceAWSImpl extends ReplayService {
    // Implementation details...
    
    @Override
    public ReplayStatusResponse getReplayStatus(String replayId) {
        // Get all replay metadata items for the given replayId
        // Calculate overall status
        // Create and return the response
    }
    
    @Override
    public ReplayResponse handleReplayRequest(ReplayRequest replayRequest) {
        // Create individual records for each kind
        // Create and publish replay messages
        // Return the response
    }
}
```

## 3. Key Implementation Features

### 3.1 Per-Kind Status Tracking

The implementation creates individual records in DynamoDB for each kind involved in a replay operation:

1. When a replay operation is initiated, a separate record is created for each kind being replayed.
2. Each record tracks the status, progress, and other metadata specific to that kind.
3. When a failure occurs for a specific kind, the system can easily find and update the corresponding record.

This approach provides several benefits:
- More granular status tracking
- Better failure handling
- Improved query performance
- Consistent with the data model used by other cloud providers

### 3.2 Asynchronous Processing

The replay operation is fully asynchronous:

1. The API endpoint returns immediately with a replay ID.
2. Messages are published to SNS topics for each kind.
3. SQS queues receive these messages and process them asynchronously.
4. Status updates are stored in DynamoDB and can be queried at any time.

This design ensures that the operation is scalable and resilient to failures.

### 3.3 Efficient Record Processing

Records are processed in batches to optimize performance:

1. Records are retrieved in pages using the `getAllRecordIdsFromKind` method.
2. Each batch is processed and published to the appropriate SNS topic.
3. Progress is tracked and updated in DynamoDB after each batch.

### 3.4 Error Handling and Retries

The implementation includes robust error handling and retry logic:

1. SQS messages are processed with exponential backoff retry logic.
2. Failed messages are sent to a dead letter queue after a maximum number of retries.
3. Failures are tracked in DynamoDB and can be queried through the API.

## 4. Configuration

### 4.1 Application Properties

The replay feature is configured through application properties:

```properties
# Replay feature flag
feature.replay.enabled=true

# AWS SQS configuration
aws.sqs.polling-interval-ms=1000

# AWS DynamoDB configuration
aws.dynamodb.replayStatusTable.ssm.relativePath=${REPLAY_REPOSITORY_SSM_RELATIVE_PATH:services/core/storage/ReplayStatusTable}

# Replay operation routing properties
replay.operation.routingProperties = { \
  reindex : { topic : '${REINDEX_TOPIC_ARN}', queryBatchSize : '5000', publisherBatchSize : '50'}, \
  replay: { topic : '${OSDU_STORAGE_TOPIC}', queryBatchSize : '5000', publisherBatchSize : '50'} \
}

# Replay routing properties
replay.routingProperties = { topic : '${REPLAY_TOPIC_ARN}' }
```

### 4.2 AWS Resources

The following AWS resources are required for the replay feature:

1. **SNS Topics**:
   - Replay topic for replay operations
   - Reindex topic for reindex operations
   - Records topic for record change messages

2. **SQS Queues**:
   - Queues that subscribe to the SNS topics
   - Dead letter queues for failed messages

3. **DynamoDB Table**:
   - ReplayStatus table with appropriate capacity settings

4. **IAM Permissions**:
   - Permissions for the service to access SNS, SQS, and DynamoDB

## 5. Implementation Challenges and Solutions

### 5.1 DynamoDB Query Patterns

The `DynamoDBQueryHelperV2` class from os-core-lib-aws doesn't have a direct `query` method that returns a list of items. We had to use the `queryPage` method instead and filter the results:

```java
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
```

### 5.2 Schema Service Integration

To get a complete list of kinds, we integrated with the Schema Service:

```java
private List<String> getAllKindsFromSchemaService() {
    // Create a REST client to call the schema service
    RestTemplate restTemplate = new RestTemplate();
    
    // Get the schema service URL from configuration
    String schemaServiceUrl = config.getSchemaApiUrl() + "/schema";
    
    // Set up headers for the request
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", this.headers.getAuthorization());
    headers.set("data-partition-id", this.headers.getPartitionId());
    headers.setContentType(MediaType.APPLICATION_JSON);
    
    // Create the HTTP entity
    HttpEntity<String> entity = new HttpEntity<>(headers);
    
    // Make the request to the schema service
    ResponseEntity<SchemaInfoResponse> response = restTemplate.exchange(
        schemaServiceUrl, 
        HttpMethod.GET, 
        entity, 
        SchemaInfoResponse.class);
    
    // Extract unique entity types (kinds) from schema infos
    return response.getBody().getSchemaInfos().stream()
        .map(schemaInfo -> schemaInfo.getSchemaIdentity().getEntityType())
        .distinct()
        .collect(Collectors.toList());
}
```

This approach ensures we have a complete list of kinds to query against, even if some kinds don't have any active records yet.

### 5.3 Request Context Management

When processing SQS messages, we need to create a proper request context with the appropriate headers:

```java
private void processMessage(Message message) {
    // Extract headers from the message
    Map<String, String> headers = extractHeaders(message);
    
    // Process the message within a request context with the extracted headers
    requestScopeUtil.executeInRequestScope(() -> {
        try {
            // Process the message
        } catch (Exception e) {
            // Handle errors
        }
    }, headers);
}
```

This ensures that all necessary context information (like partition ID and authorization) is available during message processing.

## 6. Future Enhancements

### 6.1 Integration Testing

Implement comprehensive integration tests for:
- Replay API endpoints
- Message publishing and consumption
- Status tracking and reporting

### 6.2 Monitoring Improvements

Set up CloudWatch dashboards and alarms for:
- Message processing rates
- Queue depths
- Error rates
- DynamoDB throttling events

### 6.3 Performance Optimizations

Potential areas for performance improvement:
- Batch size tuning for record retrieval and message publishing
- DynamoDB read/write capacity optimization
- Parallel processing of different kinds

### 6.4 User Documentation

Create detailed user documentation with:
- API usage examples
- Common troubleshooting scenarios
- Best practices for large-scale replay operations
