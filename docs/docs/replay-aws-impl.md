# AWS Replay Feature Implementation Plan

This document outlines the implementation plan for the Replay feature in the AWS provider for OSDU Storage Service, based on the existing Azure implementation.

## 1. Core Components to Implement

### 1.1 DynamoDB Table for Replay Status
We'll need a DynamoDB table to store replay status information, similar to Azure's Cosmos DB implementation:

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
}
```

### 1.2 AWS Repository Implementation
Create a `ReplayRepositoryImpl` for AWS that implements the `IReplayRepository` interface:

```java
@Component
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

### 1.3 Query Repository Extensions
Extend the existing `QueryRepositoryImpl` with the required methods for replay operations:

```java
@Override
public RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor) {
    // Implement to return all record IDs and kinds with pagination
}

@Override
public RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind) {
    // Implement to return all record IDs for a specific kind with pagination
}

@Override
public HashMap<String, Long> getActiveRecordsCount() {
    // Implement to return count of all active records
}

@Override
public Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds) {
    // Implement to return count of active records for specific kinds
}
```

### 1.4 SQS Message Handling
Implement SQS message handling for replay operations:

```java
@Component
public class ReplayMessageHandler {
    @Autowired
    private AmazonSQS sqsClient;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${aws.sqs.replay-queue-url}")
    private String replayQueueUrl;
    
    @Value("${aws.sqs.reindex-queue-url}")
    private String reindexQueueUrl;
    
    @Value("${aws.sqs.records-queue-url}")
    private String recordsQueueUrl;
    
    public void handle(ReplayMessage message) {
        try {
            // Process the replay message
            replayService.processReplayMessage(message);
        } catch (Exception e) {
            // Handle failure
            replayService.processFailure(message);
            throw e;
        }
    }
    
    public void handleFailure(ReplayMessage message) {
        replayService.processFailure(message);
    }
    
    public void sendReplayMessage(List<ReplayMessage> messages, String operation) {
        try {
            // Select appropriate queue based on operation
            String queueUrl = getQueueUrlForOperation(operation);
            
            for (ReplayMessage message : messages) {
                String messageBody = objectMapper.writeValueAsString(message);
                SendMessageRequest sendMessageRequest = new SendMessageRequest()
                    .withQueueUrl(queueUrl)
                    .withMessageBody(messageBody);
                
                sqsClient.sendMessage(sendMessageRequest);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize replay message", e);
        }
    }
    
    private String getQueueUrlForOperation(String operation) {
        switch (operation) {
            case "reindex":
                return reindexQueueUrl;
            case "replay":
                return recordsQueueUrl;
            default:
                return replayQueueUrl;
        }
    }
}
```

### 1.5 MessageBus Implementation Updates
Update the `MessageBusImpl` to support the new methods required for replay:

```java
@Override
public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, List<?> messageList) {
    // Get queue URL from routing info
    String queueUrl = routingInfo.get("queue");
    
    // Publish messages directly to SQS queue
    for (Object message : messageList) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            SendMessageRequest request = new SendMessageRequest()
                .withQueueUrl(queueUrl)
                .withMessageBody(messageBody);
            
            sqsClient.sendMessage(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize message", e);
        }
    }
}
```

### 1.6 AWS Replay Service Implementation
Implement the AWS-specific replay service:

```java
@Service
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayServiceAWSImpl implements IReplayService {
    @Autowired
    private ReplayRepositoryImpl replayRepository;
    
    @Autowired
    private ReplayMessageHandler messageHandler;
    
    @Autowired
    private QueryRepositoryImpl queryRepository;
    
    @Autowired
    private MessageBusImpl messageBus;
    
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
        // Process the replay message and publish record changes
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

### 1.7 SQS Message Listener
Create an SQS message listener to process replay messages:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplaySubscriptionMessageHandler implements MessageHandler {
    private final AmazonSQS sqsClient;
    private final ReplayMessageHandler replayMessageHandler;
    private final int MAX_DELIVERY_COUNT = 3;
    
    @Value("${aws.sqs.replay-queue-url}")
    private String replayQueueUrl;
    
    public ReplaySubscriptionMessageHandler(AmazonSQS sqsClient, ReplayMessageHandler replayMessageHandler) {
        this.sqsClient = sqsClient;
        this.replayMessageHandler = replayMessageHandler;
    }
    
    @Scheduled(fixedDelayString = "${aws.sqs.polling-interval-ms:1000}")
    public void pollMessages() {
        ReceiveMessageRequest receiveRequest = new ReceiveMessageRequest()
            .withQueueUrl(replayQueueUrl)
            .withMaxNumberOfMessages(10)
            .withWaitTimeSeconds(5)
            .withAttributeNames("ApproximateReceiveCount");
            
        ReceiveMessageResult result = sqsClient.receiveMessage(receiveRequest);
        
        for (Message message : result.getMessages()) {
            try {
                ReplayMessage replayMessage = objectMapper.readValue(message.getBody(), ReplayMessage.class);
                replayMessageHandler.handle(replayMessage);
                sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
            } catch (Exception e) {
                int receiveCount = Integer.parseInt(message.getAttributes().get("ApproximateReceiveCount"));
                if (receiveCount >= MAX_DELIVERY_COUNT) {
                    // Dead letter the message
                    replayMessageHandler.handleFailure(replayMessage);
                    sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
                } else {
                    // Return to queue for retry
                    sqsClient.changeMessageVisibility(replayQueueUrl, message.getReceiptHandle(), 30);
                }
            }
        }
    }
}
```

## 2. Configuration Updates

### 2.1 AWS Configuration
Add necessary configuration for SQS and DynamoDB:

```java
@Configuration
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayAwsConfig {
    @Value("${AWS.REGION}")
    private String region;
    
    @Bean
    public AmazonSQS amazonSQSClient() {
        return AmazonSQSClientBuilder.standard()
            .withRegion(region)
            .build();
    }
    
    @Bean
    public DynamoDBMapper dynamoDBMapper(AmazonDynamoDB amazonDynamoDB) {
        return new DynamoDBMapper(amazonDynamoDB);
    }
}
```

### 2.2 Application Properties
Add the following to `application.properties`, similar to Azure's configuration:

```properties
# Replay feature flag
feature.replay.enabled=true

# AWS SQS configuration
aws.sqs.replay-queue-url=${REPLAY_QUEUE_URL}
aws.sqs.reindex-queue-url=${REINDEX_QUEUE_URL}
aws.sqs.records-queue-url=${RECORDS_QUEUE_URL}
aws.sqs.polling-interval-ms=1000

# AWS DynamoDB configuration
aws.dynamodb.replay-table-name=ReplayStatus

# Replay operation routing properties
replay.operation.routingProperties = { \
  reindex : { queue : '${REINDEX_QUEUE_URL}', queryBatchSize : '5000', publisherBatchSize : '50'}, \
  replay: { queue : '${RECORDS_QUEUE_URL}', queryBatchSize : '5000', publisherBatchSize : '50'} \
}

# Replay routing properties
replay.routingProperties = { queue : '${REPLAY_QUEUE_URL}' }
```

## 3. Integration Tests

Implement integration tests for the Replay feature in the AWS testing module, similar to Azure's implementation:

```java
public class TestReplayEndpoint extends ReplayEndpointsTests {
    private static final AWSTestUtils awsTestUtils = new AWSTestUtils();

    @BeforeClass
    public static void classSetup() throws Exception {
        ReplayEndpointsTests.classSetup(awsTestUtils.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        ReplayEndpointsTests.classTearDown(awsTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AWSTestUtils();
        this.configUtils = new ConfigUtils("test.properties");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
    }
}
```

Add test properties for AWS:

```properties
# Enable/disable replay all test
test.replayAll.enabled = false

# Timeout for replay tests (in seconds)
test.replayAll.timeout = 60
```

## 4. Implementation Steps

1. **Create DynamoDB Table**: Define the ReplayStatus table in DynamoDB
2. **Extend Query Repository**: Implement the required query methods for replay operations
3. **Implement Replay Repository**: Create the AWS-specific repository implementation
4. **Update MessageBus**: Extend the MessageBusImpl with the required methods
5. **Implement Message Handling**: Set up SQS message handling for replay operations
6. **Implement Service Layer**: Create the AWS-specific replay service implementation
7. **Configure SQS Listener**: Set up the SQS message listener
8. **Update Terraform**: Add necessary resources to IaC templates
9. **Write Integration Tests**: Create comprehensive tests for the replay functionality
10. **Documentation**: Update documentation with AWS-specific details

## 5. Terraform Resources

The following AWS resources will need to be added to your Terraform IaC:

1. **SQS Queues**:
   - Main replay queue
   - Dead letter queue for failed messages
   - Topic-specific queues for different replay operations

2. **DynamoDB Table**:
   - ReplayStatus table with appropriate capacity settings

3. **IAM Permissions**:
   - Permissions for the service to access SQS and DynamoDB
   - Cross-service permissions as needed

4. **CloudWatch Alarms**:
   - Alarms for queue depth
   - Alarms for failed messages
   - Alarms for DynamoDB throttling

## 6. Considerations

1. **Error Handling**:
   - Implement robust error handling with dead-letter queues
   - Track and log all failures in CloudWatch
   - Update status in DynamoDB for failed operations

2. **Scaling**:
   - Configure auto-scaling for DynamoDB based on expected load
   - Set appropriate SQS visibility timeout for long-running operations
   - Consider using SQS FIFO queues for operations requiring strict ordering

3. **Monitoring**:
   - Add CloudWatch metrics for replay operations
   - Create dashboards for monitoring replay status
   - Set up alerts for stalled or failed replay operations

4. **Security**:
   - Ensure proper IAM permissions for accessing resources
   - Encrypt data at rest in DynamoDB
   - Encrypt messages in transit

5. **Backwards Compatibility**:
   - Maintain compatibility with existing DynamoDB schema
   - Ensure API compatibility with other cloud implementations

#### Benefits of SQS-only approach:
1. Simplified Architecture: Fewer AWS services to manage and monitor
2. Direct Control: More direct control over message delivery and processing
3. Cost Efficiency: Potentially lower costs by eliminating SNS
4. Reduced Latency: Eliminating the SNS hop can reduce message delivery latency

#### Considerations for SQS-only approach:
1. Fan-out: If multiple consumers need the same messages, you'll need to implement custom fan-out logic
2. Message Filtering: You'll need to implement custom filtering logic if needed
3. Queue Management: Need to manage multiple queues for different operations

## 7. Detailed Implementation Notes

### 7.1 DynamoDB Table Design

The ReplayStatus table will have the following structure:
- Partition Key: `replayId` (String)
- Sort Key: `id` (String) - Used to differentiate between overall status and kind-specific status
- Other attributes:
  - kind (String) - Only present for kind-specific status items
  - operation (String)
  - totalRecords (Number)
  - processedRecords (Number)
  - state (String) - One of: QUEUED, IN_PROGRESS, COMPLETED, FAILED
  - startedAt (String - ISO format date)
  - elapsedTime (String)
  - filter (String - JSON)

### 7.2 SQS Message Flow

1. User initiates replay via API
2. System creates initial status entry in DynamoDB
3. System sends initial message to appropriate SQS queue based on operation type
4. SQS listener processes message:
   • For ReplayAll: Queries for all kinds, then sends kind-specific messages to appropriate queues
   • For ReplayKind: Processes records for the specified kind
5. As processing progresses, status is updated in DynamoDB
6. Record change messages are published directly to appropriate SQS queues
7. Upon completion, final status is updated

### 7.3 Error Handling Strategy

1. Use SQS visibility timeout for retries
2. Implement exponential backoff for retries
3. After maximum retries, move message to dead-letter queue
4. Update status with error information in DynamoDB
5. Log detailed error information to CloudWatch Logs

### 7.4 Performance Considerations

1. Use DynamoDB batch operations for efficient updates
2. Configure appropriate provisioned capacity for DynamoDB
3. Use SQS batch operations for message processing
4. Implement pagination for large result sets
