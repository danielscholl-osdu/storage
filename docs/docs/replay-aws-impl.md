# AWS Replay Feature Implementation

This document outlines the implementation of the Replay feature in the AWS provider for OSDU Storage Service, based on the existing Azure implementation but adapted to use AWS-native messaging patterns with SNS and SQS.

## 1. Architecture Overview

The AWS implementation uses a consolidated SNS-SQS pattern for message publishing and consumption:

```
User Request → Storage API → Single SNS Topic → Single SQS Queue → Consumers
                    ↕
               DynamoDB (status tracking)
```

This pattern provides better decoupling between publishers and consumers while simplifying the infrastructure by using a single topic for all replay operations.

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

The `ReplayMessageHandler` class is responsible for publishing replay messages to a single SNS topic with operation-specific attributes:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayMessageHandler {
    // Implementation details...
    
    public void sendReplayMessage(List<ReplayMessage> messages, String operation) {
        for (ReplayMessage message : messages) {
            // Ensure the message has all current headers
            updateMessageWithCurrentHeaders(message);
            
            String messageBody = objectMapper.writeValueAsString(message);

            // Create message attributes for SNS
            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            
            // Add operation as a message attribute
            messageAttributes.put("operation", new MessageAttributeValue()
                .withDataType("String")
                .withStringValue(operation));
            
            // Add all headers as message attributes for SNS
            if (message.getHeaders() != null) {
                for (Map.Entry<String, String> header : message.getHeaders().entrySet()) {
                    if (header.getValue() != null) {
                        messageAttributes.put(header.getKey(), new MessageAttributeValue()
                            .withDataType("String")
                            .withStringValue(header.getValue()));
                    }
                }
            }

            PublishRequest publishRequest = new PublishRequest()
                .withTopicArn(replayTopicArn)
                .withMessage(messageBody)
                .withMessageAttributes(messageAttributes);

            snsClient.publish(publishRequest);
        }
    }
}
```

#### 2.3.2 Consuming Messages with SQS

The `ReplaySubscriptionMessageHandler` class polls a single SQS queue for replay messages and processes them based on the operation attribute:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplaySubscriptionMessageHandler {
    // Implementation details...
    
    @Scheduled(fixedDelayString = "${aws.sqs.polling-interval-ms:1000}")
    public void pollMessages() {
        // Poll the SQS queue for messages
        ReceiveMessageRequest receiveRequest = new ReceiveMessageRequest()
            .withQueueUrl(replayQueueUrl)
            .withMaxNumberOfMessages(10)
            .withWaitTimeSeconds(5)
            .withAttributeNames("ApproximateReceiveCount")
            .withMessageAttributeNames("All"); // Request all message attributes
            
        ReceiveMessageResult result = sqsClient.receiveMessage(receiveRequest);
        
        // Process each message in its own request context
        for (Message message : result.getMessages()) {
            processMessage(message);
        }
    }
    
    private void processMessage(Message message) {
        // Extract headers and operation type from message attributes
        Map<String, String> headers = extractHeaders(message);
        String operation = "unknown";
        if (message.getMessageAttributes() != null && message.getMessageAttributes().containsKey("operation")) {
            operation = message.getMessageAttributes().get("operation").getStringValue();
        }
        
        // Process the message within a request context with the extracted headers
        requestScopeUtil.executeInRequestScope(() -> {
            try {
                // Process the message
                ReplayMessage replayMessage = extractReplayMessage(message);
                replayMessageHandler.handle(replayMessage);
                sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
            } catch (Exception e) {
                // Handle errors with exponential backoff
                handleMessageError(message, e);
            }
        }, headers);
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

### 3.1 Consolidated Messaging Approach

The implementation uses a single SNS topic for all replay operations:

1. Different operation types (replay, reindex) are distinguished by message attributes.
2. A single SQS queue subscribes to the SNS topic and processes all messages.
3. The operation type is extracted from message attributes during processing.

This approach simplifies the infrastructure and reduces the number of AWS resources needed.

### 3.2 Per-Kind Status Tracking

The implementation creates individual records in DynamoDB for each kind involved in a replay operation:

1. When a replay operation is initiated, a separate record is created for each kind being replayed.
2. Each record tracks the status, progress, and other metadata specific to that kind.
3. When a failure occurs for a specific kind, the system can easily find and update the corresponding record.

This approach provides several benefits:
- More granular status tracking
- Better failure handling
- Improved query performance
- Consistent with the data model used by other cloud providers

### 3.3 Asynchronous Processing

The replay operation is fully asynchronous:

1. The API endpoint returns immediately with a replay ID.
2. Messages are published to the SNS topic for each kind.
3. The SQS queue receives these messages and processes them asynchronously.
4. Status updates are stored in DynamoDB and can be queried at any time.

This design ensures that the operation is scalable and resilient to failures.

### 3.4 Efficient Record Processing

Records are processed in batches to optimize performance:

1. Records are retrieved in pages using the `getAllRecordIdsFromKind` method.
2. Each batch is processed and published to the appropriate SNS topic.
3. Progress is tracked and updated in DynamoDB after each batch.

### 3.5 Error Handling and Retries

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

# Replay operation routing properties - Used by core code
replay.operation.routingProperties = { \
  reindex : { topic : '${REPLAY_TOPIC:replay-records}', queryBatchSize : '5000', publisherBatchSize : '50'}, \
  replay: { topic : '${REPLAY_TOPIC:replay-records}', queryBatchSize : '5000', publisherBatchSize : '50'} \
}

# Replay routing properties - Used by core code
replay.routingProperties = { topic : '${REPLAY_TOPIC:replay-records}' }

# Replay topic environment variable
REPLAY_TOPIC=${REPLAY_TOPIC:replay-records}
```

### 4.2 AWS Resources

The following AWS resources are required for the replay feature:

1. **SNS Topic**:
   - A single replay topic for all replay operations

2. **SQS Queue**:
   - A queue that subscribes to the SNS topic
   - A dead letter queue for failed messages

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

### 5.4 Message Attribute Handling

To support the consolidated approach, we needed to add operation type as a message attribute:

```java
// Add operation as a message attribute
messageAttributes.put("operation", new MessageAttributeValue()
    .withDataType("String")
    .withStringValue(operation));
```

And then extract it during message processing:

```java
String operation = "unknown";
if (message.getMessageAttributes() != null && message.getMessageAttributes().containsKey("operation")) {
    operation = message.getMessageAttributes().get("operation").getStringValue();
}
```

This allows us to use a single topic for multiple operation types.

## 6. Benefits of the Consolidated Approach

### 6.1 Simplified Infrastructure

Using a single SNS topic and SQS queue for all replay operations provides several benefits:

1. **Reduced Resource Count**: Fewer AWS resources to manage and monitor
2. **Simplified Configuration**: Single topic ARN and queue URL to configure
3. **Consistent Processing**: All replay operations follow the same processing pattern
4. **Easier Monitoring**: Single queue for monitoring message throughput
5. **Simplified Error Handling**: Consolidated dead letter queue handling

### 6.2 Flexible Operation Types

The message attribute-based approach allows for easy addition of new operation types:

1. **Extensible Design**: New operation types can be added without infrastructure changes
2. **Clear Differentiation**: Operation types are clearly identified in message attributes
3. **Consistent Processing**: All operations use the same processing pipeline

## 7. Future Enhancements

### 7.1 Integration Testing

Implement comprehensive integration tests for:
- Replay API endpoints
- Message publishing and consumption
- Status tracking and reporting

### 7.2 Monitoring Improvements

Set up CloudWatch dashboards and alarms for:
- Message processing rates
- Queue depths
- Error rates
- DynamoDB throttling events

### 7.3 Performance Optimizations

Potential areas for performance improvement:
- Batch size tuning for record retrieval and message publishing
- DynamoDB read/write capacity optimization
- Parallel processing of different kinds

### 7.4 User Documentation

Create detailed user documentation with:
- API usage examples
- Common troubleshooting scenarios
- Best practices for large-scale replay operations
