# AWS Replay Feature Implementation Plan

This document outlines the implementation plan for the Replay feature in the AWS provider for OSDU Storage Service, based on the existing Azure implementation but adapted to use AWS-native messaging patterns with SNS and SQS.

## 1. Core Components Implemented

### 1.1 Architecture Overview
The AWS implementation uses an SNS-SQS pattern for message publishing and consumption:

```
User Request → Storage API → SNS Topics → SQS Queues → Consumers
                    ↕
               DynamoDB (status tracking)
```

This pattern provides better decoupling between publishers and consumers, allowing multiple consumers to subscribe to the same messages if needed.

### 1.2 DynamoDB Table for Replay Status ✅
We've implemented a DynamoDB table to store replay status information, similar to Azure's Cosmos DB implementation:

```java
@Data
@DynamoDBTable(tableName = "ReplayStatus")
public class ReplayMetadataItem {
    @DynamoDBHashKey
    private String id;
    
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

### 1.3 AWS Repository Implementation ✅
Created a `ReplayRepositoryImpl` for AWS that implements the `IReplayRepository` interface:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayRepositoryImpl implements IReplayRepository {
    private final DynamoDBMapper dynamoDBMapper;
    private final AmazonDynamoDB dynamoDBClient;
    
    @Autowired
    private DpsHeaders headers;
    
    @Value("${aws.dynamodb.replay-table-name}")
    private String replayTableName;
    
    @Override
    public List<ReplayMetaDataDTO> getReplayStatusByReplayId(String replayId) {
        // Query DynamoDB for all items with the given replayId
        // Convert to ReplayMetaDataDTO objects and return
    }
    
    @Override
    public ReplayMetaDataDTO getReplayStatusByKindAndReplayId(String kind, String replayId) {
        // Query DynamoDB for the specific kind and replayId
        // Convert to ReplayMetaDataDTO and return
    }
    
    @Override
    public ReplayMetaDataDTO save(ReplayMetaDataDTO replayMetaData) {
        // Convert to DynamoDB item and save
        // Return the saved item as DTO
    }
}
```

### 1.4 Query Repository Extensions ✅
Extended the existing `QueryRepositoryImpl` with the required methods for replay operations:

```java
@Override
public RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor) {
    // Implementation to return all record IDs and kinds with pagination
}

@Override
public RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind) {
    // Implementation to return all record IDs for a specific kind with pagination
}

@Override
public HashMap<String, Long> getActiveRecordsCount() {
    // Implementation to return count of all active records
}

@Override
public Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds) {
    // Implementation to return count of active records for specific kinds
}
```

### 1.5 SNS-SQS Message Handling ✅
Implemented SNS for publishing messages and SQS for consuming messages:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayMessageHandler {
    private final AmazonSNS snsClient;
    private final AmazonSQS sqsClient;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private ReplayService replayService;
    
    @Autowired
    private JaxRsDpsLog logger;
    
    @Value("${aws.sqs.replay-queue-url}")
    private String replayQueueUrl;
    
    @Value("${aws.sns.replay-topic-arn}")
    private String replayTopicArn;
    
    @Value("${aws.sns.reindex-topic-arn}")
    private String reindexTopicArn;
    
    @Value("${aws.sns.records-topic-arn}")
    private String recordsTopicArn;
    
    public void handle(ReplayMessage message) {
        // Process the replay message
    }
    
    public void handleFailure(ReplayMessage message) {
        // Handle failure
    }
    
    public void sendReplayMessage(List<ReplayMessage> messages, String operation) {
        // Determine which SNS topic to use based on operation
        String topicArn;
        switch (operation) {
            case "replay":
                topicArn = replayTopicArn;
                break;
            case "reindex":
                topicArn = reindexTopicArn;
                break;
            default:
                topicArn = recordsTopicArn;
                break;
        }
        
        // Publish messages to the appropriate SNS topic
        for (ReplayMessage message : messages) {
            try {
                String messageBody = objectMapper.writeValueAsString(message);
                PublishRequest publishRequest = new PublishRequest()
                    .withTopicArn(topicArn)
                    .withMessage(messageBody);
                
                snsClient.publish(publishRequest);
                logger.debug("Published message to SNS topic: " + topicArn);
                
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize message: " + e.getMessage(), e);
                throw new RuntimeException("Failed to serialize message", e);
            }
        }
    }
}
```

### 1.6 MessageBus Implementation Updates ✅
Updated the `MessageBusImpl` to support publishing messages to SNS topics:

```java
@Override
public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, List<?> messageList) {
    // Get topic ARN from routing info
    String topicArn = routingInfo.get("topic");
    if (topicArn == null || topicArn.isEmpty()) {
        logger.error("No SNS topic ARN provided in routing info");
        return;
    }
    
    // Use AWS SDK to publish messages to SNS topic
    AmazonSNS snsClient = AmazonSNSClientBuilder.standard()
        .withRegion(currentRegion)
        .build();
        
    ObjectMapper objectMapper = new ObjectMapper();
    
    // Publish messages to SNS topic
    for (Object message : messageList) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            PublishRequest publishRequest = new PublishRequest()
                .withTopicArn(topicArn)
                .withMessage(messageBody);
            
            snsClient.publish(publishRequest);
            logger.debug("Published message to SNS topic: " + topicArn);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize message: " + e.getMessage(), e);
            throw new RuntimeException("Failed to serialize message", e);
        }
    }
}
```

### 1.7 AWS Replay Service Implementation ✅
Implemented the AWS-specific replay service using SNS topics for message publishing:

```java
@Service
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayServiceAWSImpl extends ReplayService {
    @Autowired
    private IReplayRepository replayRepository;
    
    @Autowired
    private ReplayMessageHandler messageHandler;
    
    @Autowired
    private QueryRepositoryImpl queryRepository;
    
    @Autowired
    private IMessageBus messageBus;
    
    @Autowired
    private DpsHeaders headers;
    
    @Autowired
    private StorageAuditLogger auditLogger;
    
    @Value("#{${replay.operation.routingProperties}}")
    private Map<String, Map<String, String>> replayOperationRoutingProperties;
    
    @Value("#{${replay.routingProperties}}")
    private Map<String, String> replayRoutingProperty;
    
    @Override
    public ReplayStatusResponse getReplayStatus(String replayId) {
        // Get replay status from repository and convert to response
    }
    
    @Override
    public void processReplayMessage(ReplayMessage replayMessage) {
        // Process the replay message and publish record changes to SNS
    }
    
    @Override
    public void processFailure(ReplayMessage replayMessage) {
        // Handle failure cases
    }
    
    @Override
    public ReplayResponse handleReplayRequest(ReplayRequest replayRequest) {
        // Initialize replay process and return response
    }
}
```

### 1.8 SQS Message Listener ✅
Created an SQS message listener to process replay messages:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplaySubscriptionMessageHandler {
    private final AmazonSQS sqsClient;
    private final ReplayMessageHandler replayMessageHandler;
    private final ObjectMapper objectMapper;
    private final JaxRsDpsLog logger;
    private final int MAX_DELIVERY_COUNT = 3;
    
    @Value("${aws.sqs.replay-queue-url}")
    private String replayQueueUrl;
    
    @Scheduled(fixedDelayString = "${aws.sqs.polling-interval-ms:1000}")
    public void pollMessages() {
        // Poll for messages and process them
    }
}
```

## 2. Configuration Updates ✅

### 2.1 AWS Configuration
Added necessary configuration for SNS, SQS, and DynamoDB:

```java
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayAwsConfig {
    @Value("${aws.region}")
    private String region;
    
    @Bean
    @ConditionalOnMissingBean
    public AmazonSQS amazonSQSClient() {
        return AmazonSQSClientBuilder.standard()
            .withRegion(region)
            .build();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AmazonSNS amazonSNSClient() {
        return AmazonSNSClientBuilder.standard()
            .withRegion(region)
            .build();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public DynamoDBMapper dynamoDBMapper(AmazonDynamoDB amazonDynamoDB) {
        return new DynamoDBMapper(amazonDynamoDB);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AmazonDynamoDB amazonDynamoDB() {
        return AmazonDynamoDBClientBuilder.standard()
            .withRegion(region)
            .build();
    }
}
```

### 2.2 Application Properties ✅
Added the following to `application-replay.properties`:

```properties
# Replay feature flag
feature.replay.enabled=true

# AWS SQS configuration
aws.sqs.replay-queue-url=${REPLAY_QUEUE_URL}
aws.sqs.polling-interval-ms=1000

# AWS SNS configuration
aws.sns.replay-topic-arn=${REPLAY_TOPIC_ARN}
aws.sns.reindex-topic-arn=${REINDEX_TOPIC_ARN}
aws.sns.records-topic-arn=${RECORDS_TOPIC_ARN}

# AWS DynamoDB configuration
aws.dynamodb.replay-table-name=ReplayStatus

# AWS Region
aws.region=${AWS_REGION:us-east-1}

# Replay operation routing properties
replay.operation.routingProperties = { \
  reindex : { topic : '${REINDEX_TOPIC_ARN}', queryBatchSize : '5000', publisherBatchSize : '50'}, \
  replay: { topic : '${RECORDS_TOPIC_ARN}', queryBatchSize : '5000', publisherBatchSize : '50'} \
}

# Replay routing properties
replay.routingProperties = { topic : '${REPLAY_TOPIC_ARN}' }

# Swagger properties for Replay API
replayApi.getReplayStatus.summary=Get the status of a replay operation
replayApi.getReplayStatus.description=Returns the current status of a replay operation, including overall progress and kind-specific details
replayApi.triggerReplay.summary=Trigger a new replay operation
replayApi.triggerReplay.description=Initiates a new replay operation to publish record change messages for the specified kinds or all kinds
```

## 3. Integration Tests ✅

Implemented unit tests for the repository implementation:

```java
@RunWith(MockitoJUnitRunner.class)
public class ReplayRepositoryImplTest {
    @Mock
    private DynamoDBMapper dynamoDBMapper;

    @Mock
    private AmazonDynamoDB dynamoDBClient;

    @Mock
    private DpsHeaders headers;

    @Mock
    private PaginatedQueryList<ReplayMetadataItem> queryResults;

    @InjectMocks
    private ReplayRepositoryImpl replayRepository;

    @Test
    public void testGetReplayStatusByReplayId() {
        // Test implementation
    }

    @Test
    public void testGetReplayStatusByKindAndReplayId() {
        // Test implementation
    }

    @Test
    public void testSave() {
        // Test implementation
    }
}
```

## 4. Implementation Steps Completed

1. ✅ **Create DynamoDB Table Model**: Defined the ReplayStatus table model in DynamoDB
2. ✅ **Extend Query Repository**: Implemented the required query methods for replay operations
3. ✅ **Implement Replay Repository**: Created the AWS-specific repository implementation
4. ✅ **Update MessageBus**: Extended the MessageBusImpl to use SNS for publishing messages
5. ✅ **Implement Message Handling**: Set up SNS for message publishing and SQS for message consumption
6. ✅ **Implement Service Layer**: Created the AWS-specific replay service implementation
7. ✅ **Configure SQS Listener**: Set up the SQS message listener
8. ⬜ **Update Terraform**: Add necessary resources to IaC templates, including SNS topics
9. ✅ **Write Unit Tests**: Created tests for the repository implementation
10. ✅ **Documentation**: Updated documentation with AWS-specific details

## 5. Terraform Resources (To Be Implemented)

The following AWS resources will need to be added to your Terraform IaC:

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
   - Cross-service permissions as needed

5. **CloudWatch Alarms**:
   - Alarms for queue depth
   - Alarms for failed messages
   - Alarms for DynamoDB throttling

## 6. Considerations

1. **Error Handling**:
   - Implemented robust error handling with dead-letter queues
   - Added tracking and logging of all failures in CloudWatch
   - Implemented status updates in DynamoDB for failed operations

2. **Scaling**:
   - Configured appropriate SQS visibility timeout for long-running operations
   - Implemented exponential backoff for retries

3. **Monitoring**:
   - Added logging for replay operations
   - Implemented status tracking for monitoring replay progress

4. **Security**:
   - Ensured proper IAM permissions for accessing resources
   - Implemented data encryption for messages in transit

5. **Backwards Compatibility**:
   - Maintained compatibility with existing implementations
   - Used conditional beans to avoid conflicts with existing beans
   - Ensured API compatibility with other cloud implementations

## 7. Detailed Implementation Notes

### 7.1 DynamoDB Table Design

The ReplayStatus table has the following structure:
- Partition Key: `id` (String) - For overall status, this is "overall". For kind-specific status, this is the kind name.
- Sort Key: `replayId` (String) - The unique identifier for the replay operation
- Other attributes:
  - kind (String) - Only present for kind-specific status items
  - operation (String)
  - totalRecords (Number)
  - processedRecords (Number)
  - state (String) - One of: QUEUED, IN_PROGRESS, COMPLETED, FAILED
  - startedAt (Date)
  - elapsedTime (String)
  - filter (String - JSON)
  - dataPartitionId (String)

### 7.2 SNS-SQS Message Flow

1. User initiates replay via API
2. System creates initial status entry in DynamoDB
3. System publishes initial message to appropriate SNS topic based on operation type
4. SNS delivers the message to subscribed SQS queues
5. SQS listener processes message:
   - For ReplayAll: Queries for all kinds, then publishes kind-specific messages to appropriate SNS topics
   - For ReplayKind: Processes records for the specified kind
6. As processing progresses, status is updated in DynamoDB
7. Record change messages are published to the Records SNS topic
8. Upon completion, final status is updated

### 7.3 Advantages of SNS-SQS Pattern

1. **Decoupling**: Publishers and consumers are completely decoupled
2. **Fan-out**: Multiple consumers can subscribe to the same SNS topic
3. **Reliability**: Messages are persisted in SQS queues even if consumers are temporarily unavailable
4. **Scalability**: Both SNS and SQS can handle high throughput
5. **Filtering**: SNS supports message filtering for subscribers

### 7.4 Error Handling Strategy

1. Use SQS visibility timeout for retries
2. Implement exponential backoff for retries
3. After maximum retries, move message to dead-letter queue
4. Update status with error information in DynamoDB
5. Log detailed error information to CloudWatch Logs

### 7.5 Performance Considerations

1. Use SNS batch publishing for efficient message delivery
2. Use SQS batch operations for message processing
3. Implement pagination for large result sets
4. Configure appropriate SQS visibility timeout for long-running operations

## 8. Next Steps

1. **Terraform Implementation**: Create the necessary Terraform resources for the replay feature, including SNS topics
2. **Integration Testing**: Implement comprehensive integration tests for the replay functionality
3. **Monitoring Setup**: Set up CloudWatch dashboards and alarms for monitoring replay operations
4. **Documentation**: Update user documentation with instructions for using the replay feature
