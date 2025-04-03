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

### 1.3 AWS Repository Implementation Using os-core-lib-aws ✅
Created a `ReplayRepositoryImpl` for AWS that implements the `IReplayRepository` interface, leveraging the os-core-lib-aws library:

```java
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
    
    @Override
    public List<ReplayMetaDataDTO> getReplayStatusByReplayId(String replayId) {
        DynamoDBQueryHelperV2 queryHelper = getReplayStatusQueryHelper();
        
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
    
    @Override
    public ReplayMetaDataDTO getReplayStatusByKindAndReplayId(String kind, String replayId) {
        DynamoDBQueryHelperV2 queryHelper = getReplayStatusQueryHelper();
        
        ReplayMetadataItem item = queryHelper.loadByPrimaryKey(ReplayMetadataItem.class, kind, replayId);
        
        return item != null ? convertToDTO(item) : null;
    }
    
    @Override
    public ReplayMetaDataDTO save(ReplayMetaDataDTO replayMetaData) {
        DynamoDBQueryHelperV2 queryHelper = getReplayStatusQueryHelper();
        
        ReplayMetadataItem item = convertToItem(replayMetaData);
        queryHelper.save(item);
        
        return convertToDTO(item);
    }
}
```

### 1.4 Query Repository Extensions ✅
Extended the existing `QueryRepositoryImpl` with the required methods for replay operations, now retrieving unique kinds directly from the record metadata table instead of the schema repository table:

```java
@Override
public RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor) {
    DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
    
    // Set the page size or use the default constant
    int numRecords = PAGE_SIZE;
    if (limit != null) {
        numRecords = limit > 0 ? limit : PAGE_SIZE;
    }
    
    RecordInfoQueryResult<RecordIdAndKind> result = new RecordInfoQueryResult<>();
    List<RecordIdAndKind> records = new ArrayList<>();
    
    try {
        // Query for all active records
        RecordMetadataDoc queryObject = new RecordMetadataDoc();
        queryObject.setStatus("active");
        
        QueryPageResult<RecordMetadataDoc> queryPageResult = recordMetadataQueryHelper.queryByGSI(
            RecordMetadataDoc.class, 
            queryObject, 
            numRecords, 
            cursor);
        
        // Convert to RecordIdAndKind objects
        for (RecordMetadataDoc doc : queryPageResult.results) {
            RecordIdAndKind record = new RecordIdAndKind();
            record.setId(doc.getId());
            record.setKind(doc.getKind());
            records.add(record);
        }
        
        result.setResults(records);
        result.setCursor(queryPageResult.cursor);
        
    } catch (UnsupportedEncodingException e) {
        throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                e.getMessage(), e);
    }
    
    return result;
}

@Override
public RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind) {
    DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
    
    // Set the page size or use the default constant
    int numRecords = PAGE_SIZE;
    if (limit != null) {
        numRecords = limit > 0 ? limit : PAGE_SIZE;
    }
    
    RecordInfoQueryResult<RecordId> result = new RecordInfoQueryResult<>();
    List<RecordId> records = new ArrayList<>();
    
    try {
        // Set GSI hash key
        RecordMetadataDoc recordMetadataKey = new RecordMetadataDoc();
        recordMetadataKey.setKind(kind);
        
        QueryPageResult<RecordMetadataDoc> scanPageResults = recordMetadataQueryHelper.queryPage(
            RecordMetadataDoc.class,
            recordMetadataKey,
            "Status",
            "active",
            "Id",
            ComparisonOperator.BEGINS_WITH,
            String.format("%s:", headers.getPartitionId()),
            numRecords,
            cursor);
        
        // Convert to RecordId objects
        for (RecordMetadataDoc doc : scanPageResults.results) {
            RecordId record = new RecordId();
            record.setId(doc.getId());
            records.add(record);
        }
        
        result.setResults(records);
        result.setCursor(scanPageResults.cursor);
        
    } catch (UnsupportedEncodingException e) {
        throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                e.getMessage(), e);
    }
    
    return result;
}

@Override
public HashMap<String, Long> getActiveRecordsCount() {
    // First, get all distinct kinds from the schema service
    HashMap<String, Long> kindCounts = new HashMap<>();
    
    try {
        // Get all kinds from schema service
        List<String> kinds = getAllKindsFromSchemaService();
        
        // Now count active records for each kind
        for (String kind : kinds) {
            long count = getActiveRecordCountForKind(kind);
            if (count > 0) {
                kindCounts.put(kind, count);
            }
        }
        
        return kindCounts;
        
    } catch (Exception e) {
        throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error retrieving active records count",
                e.getMessage(), e);
    }
}

@Override
public Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds) {
    Map<String, Long> kindCounts = new HashMap<>();
    DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
    
    for (String kind : kinds) {
        try {
            // Set GSI hash key
            RecordMetadataDoc recordMetadataKey = new RecordMetadataDoc();
            recordMetadataKey.setKind(kind);
            recordMetadataKey.setStatus("active");
            
            // Count active records for this kind using the record metadata table
            long count = 0;
            QueryPageResult<RecordMetadataDoc> queryPageResult = recordMetadataQueryHelper.queryByGSI(
                RecordMetadataDoc.class, 
                recordMetadataKey, 
                1000, 
                null);
            
            count += queryPageResult.results.size();
            
            // Process any additional pages if needed
            String cursor = queryPageResult.cursor;
            while (cursor != null && !cursor.isEmpty()) {
                queryPageResult = recordMetadataQueryHelper.queryByGSI(
                    RecordMetadataDoc.class, 
                    recordMetadataKey, 
                    1000, 
                    cursor);
                
                count += queryPageResult.results.size();
                cursor = queryPageResult.cursor;
            }
            
            kindCounts.put(kind, count);
            
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                    e.getMessage(), e);
        }
    }
    
    return kindCounts;
}
```

### 1.5 SNS-SQS Message Handling Using os-core-lib-aws ✅
Implemented SNS for publishing messages and SQS for consuming messages using the os-core-lib-aws library:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayMessageHandler {
    private AmazonSNS snsClient;
    
    @Inject
    private ObjectMapper objectMapper;
    
    @Inject
    private ReplayService replayService;
    
    @Inject
    private JaxRsDpsLog logger;
    
    @Value("${AWS.REGION}")
    private String region;
    
    @Value("${REPLAY_TOPIC}")
    private String replayTopic;
    
    @Value("${REINDEX_TOPIC}")
    private String reindexTopic;
    
    @Value("${RECORDS_TOPIC}")
    private String recordsTopic;
    
    private String replayTopicArn;
    private String reindexTopicArn;
    private String recordsTopicArn;
    
    @PostConstruct
    public void init() throws K8sParameterNotFoundException {
        // Initialize SNS client
        snsClient = new AmazonSNSConfig(region).AmazonSNS();
        
        // Get topic ARNs from SSM parameters
        K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
        try {
            replayTopicArn = provider.getParameterAsString(replayTopic + "-sns-topic-arn");
            reindexTopicArn = provider.getParameterAsString(reindexTopic + "-sns-topic-arn");
            recordsTopicArn = provider.getParameterAsString(recordsTopic + "-sns-topic-arn");
        } catch (K8sParameterNotFoundException e) {
            logger.error("Failed to retrieve SNS topic ARNs from SSM: " + e.getMessage(), e);
            throw e;
        }
    }
    
    public void sendReplayMessage(List<ReplayMessage> messages, String operation) {
        try {
            // Select appropriate topic based on operation
            String topicArn = getTopicArnForOperation(operation);
            
            for (ReplayMessage message : messages) {
                String messageBody = objectMapper.writeValueAsString(message);
                
                // Use PublishRequestBuilder from os-core-lib-aws
                PublishRequestBuilder requestBuilder = new PublishRequestBuilder()
                    .withTopicArn(topicArn)
                    .withMessage(messageBody);
                
                PublishRequest publishRequest = requestBuilder.build();
                snsClient.publish(publishRequest);
                logger.info("Published replay message to SNS topic: " + topicArn + " for replayId: " + message.getBody().getReplayId());
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize replay message: " + e.getMessage(), e);
            throw new RuntimeException("Failed to serialize replay message", e);
        }
    }
}
```

### 1.6 SQS Message Listener Using os-core-lib-aws ✅
Created an SQS message listener to process replay messages using the os-core-lib-aws library:

```java
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplaySubscriptionMessageHandler {
    private AmazonSQS sqsClient;
    
    @Inject
    private ReplayMessageHandler replayMessageHandler;
    
    @Inject
    private ObjectMapper objectMapper;
    
    @Inject
    private JaxRsDpsLog logger;
    
    private final int MAX_DELIVERY_COUNT = 3;
    
    @Value("${AWS.REGION}")
    private String region;
    
    @Value("${REPLAY_TOPIC}")
    private String replayTopic;
    
    private String replayQueueUrl;
    
    @PostConstruct
    public void init() throws K8sParameterNotFoundException {
        // Initialize SQS client
        AmazonSQSConfig sqsConfig = new AmazonSQSConfig(region);
        this.sqsClient = sqsConfig.AmazonSQS();
        
        // Get queue URL from SSM parameters
        K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
        try {
            replayQueueUrl = provider.getParameterAsString(replayTopic + "-sqs-queue-url");
        } catch (K8sParameterNotFoundException e) {
            logger.error("Failed to retrieve SQS queue URL from SSM: " + e.getMessage(), e);
            throw e;
        }
    }
    
    @Scheduled(fixedDelayString = "${aws.sqs.polling-interval-ms:1000}")
    public void pollMessages() {
        if (replayQueueUrl == null) {
            logger.error("SQS queue URL is not initialized. Skipping message polling.");
            return;
        }
        
        ReceiveMessageRequest receiveRequest = new ReceiveMessageRequest()
            .withQueueUrl(replayQueueUrl)
            .withMaxNumberOfMessages(10)
            .withWaitTimeSeconds(5)
            .withAttributeNames("ApproximateReceiveCount");
            
        ReceiveMessageResult result = sqsClient.receiveMessage(receiveRequest);
        
        for (Message message : result.getMessages()) {
            // Process messages with exponential backoff retry logic
            // Implementation details...
        }
    }
}
```

## 2. Configuration Updates ✅

### 2.1 AWS Configuration
Created a main AWS configuration class that provides the necessary beans for the entire application:

```java
@Configuration
public class AwsConfig {
    
    @Value("${AWS.REGION}")
    private String region;
    
    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;
    
    @Value("${aws.dynamodb.recordMetadataTable.ssm.relativePath}")
    private String tableName;
    
    @Bean
    @Primary
    public AmazonSQS amazonSQSClient() {
        AmazonSQSConfig sqsConfig = new AmazonSQSConfig(region);
        return sqsConfig.AmazonSQS();
    }
    
    @Bean
    @Primary
    public AmazonSNS amazonSNSClient() {
        AmazonSNSConfig snsConfig = new AmazonSNSConfig(region);
        return snsConfig.AmazonSNS();
    }
    
    @Bean
    @Primary
    public IDynamoDBConfig dynamoDBConfig() {
        return new DynamoDBConfigV2(
            dynamoDbEndpoint.isEmpty() ? "https://dynamodb." + region + ".amazonaws.com" : dynamoDbEndpoint,
            region,
            tableName,
            ConfigSetup.setUpConfig()
        );
    }
    
    @Bean
    @Primary
    public DynamoDBMapper dynamoDBMapper(IDynamoDBConfig dynamoDBConfig) {
        return dynamoDBConfig.DynamoDBMapper();
    }
    
    @Bean
    @Primary
    public AmazonDynamoDB amazonDynamoDB(IDynamoDBConfig dynamoDBConfig) {
        return dynamoDBConfig.amazonDynamoDB();
    }
}
```

### 2.2 Application Properties ✅
Updated `application-replay.properties` to use SSM parameter paths and topic names:

```properties
# Replay feature flag
feature.replay.enabled=true

# AWS SQS configuration
aws.sqs.polling-interval-ms=1000

# AWS SNS configuration
REPLAY_TOPIC=${REPLAY_TOPIC:replay-records}
REINDEX_TOPIC=${REINDEX_TOPIC:reindex-records}
RECORDS_TOPIC=${OSDU_STORAGE_TOPIC:records-change}

# AWS DynamoDB configuration
aws.dynamodb.replayStatusTable.ssm.relativePath=${REPLAY_REPOSITORY_SSM_RELATIVE_PATH:services/core/storage/ReplayStatusTable}

# AWS Region
AWS.REGION=${AWS_REGION:us-east-1}

# Replay operation routing properties
replay.operation.routingProperties = { \
  reindex : { topic : '${REINDEX_TOPIC_ARN}', queryBatchSize : '5000', publisherBatchSize : '50'}, \
  replay: { topic : '${OSDU_STORAGE_TOPIC}', queryBatchSize : '5000', publisherBatchSize : '50'} \
}

# Replay routing properties
replay.routingProperties = { topic : '${REPLAY_TOPIC_ARN}' }

# Swagger properties for Replay API
replayApi.getReplayStatus.summary=Get the status of a replay operation
replayApi.getReplayStatus.description=Returns the current status of a replay operation, including overall progress and kind-specific details
replayApi.triggerReplay.summary=Trigger a new replay operation
replayApi.triggerReplay.description=Initiates a new replay operation to publish record change messages for the specified kinds or all kinds
```

## 3. Implementation Challenges and Solutions

### 3.1 DynamoDB Query Helper
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

### 3.2 Retrieving Unique Kinds and Counting Records

We encountered a "No hash key condition is found in the query" error when trying to query DynamoDB without specifying a hash key. To solve this, we implemented a solution that uses the Schema Service to get all kinds and then queries each kind individually:

1. **Get all kinds from Schema Service**: Instead of trying to scan the entire record metadata table without a hash key, we first get all available kinds from the Schema Service.

2. **Query each kind individually**: For each kind, we query the record metadata table using the KindStatusIndex GSI with Kind as the hash key and Status as the range key.

This approach has several advantages:
- **Proper use of DynamoDB indexes**: Uses the GSI correctly with both hash and range keys
- **Efficient queries**: Each query is focused on a specific kind
- **Accurate counts**: Only counts records that actually exist in the database

The implementation first retrieves all kinds from the Schema Service:

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

Then, for each kind, it counts the active records:

```java
private long getActiveRecordCountForKind(String kind) {
    DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
    
    try {
        // Set up query parameters using the KindStatusIndex GSI
        RecordMetadataDoc recordMetadataKey = new RecordMetadataDoc();
        recordMetadataKey.setKind(kind);
        recordMetadataKey.setStatus("active");
        
        // Count active records for this kind
        long count = 0;
        QueryPageResult<RecordMetadataDoc> queryPageResult = recordMetadataQueryHelper.queryByGSI(
            RecordMetadataDoc.class, 
            recordMetadataKey, 
            1000, 
            null);
        
        count += queryPageResult.results.size();
        
        // Process any additional pages
        String cursor = queryPageResult.cursor;
        while (cursor != null && !cursor.isEmpty()) {
            queryPageResult = recordMetadataQueryHelper.queryByGSI(
                RecordMetadataDoc.class, 
                recordMetadataKey, 
                1000, 
                cursor);
            
            count += queryPageResult.results.size();
            cursor = queryPageResult.cursor;
        }
        
        return count;
    } catch (UnsupportedEncodingException e) {
        logger.error("Error counting records for kind " + kind + ": " + e.getMessage(), e);
        return 0;
    }
}
```

### 3.3 DynamoDBMapper Bean Injection
The `QueryRepositoryImpl` class was expecting a `DynamoDBMapper` bean to be injected through its constructor. We created a comprehensive AWS configuration class that provides this bean:

```java
@Bean
@Primary
public DynamoDBMapper dynamoDBMapper(IDynamoDBConfig dynamoDBConfig) {
    return dynamoDBConfig.DynamoDBMapper();
}
```

### 3.4 Data Partition ID Field
The `ReplayMetaDataDTO` class doesn't have a `dataPartitionId` field, but the `ReplayMetadataItem` class does. We had to handle this mismatch in the conversion methods:

```java
private ReplayMetaDataDTO convertToDTO(ReplayMetadataItem item) {
    ReplayMetaDataDTO dto = new ReplayMetaDataDTO();
    // Set other fields...
    
    // Note: ReplayMetaDataDTO doesn't have a dataPartitionId field, so we don't set it
    
    return dto;
}

private ReplayMetadataItem convertToItem(ReplayMetaDataDTO dto) {
    ReplayMetadataItem item = new ReplayMetadataItem();
    // Set other fields...
    
    // Set the data partition ID from the headers
    item.setDataPartitionId(headers.getPartitionId());
    
    return item;
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
8. ✅ **Update Terraform**: Added necessary resources to IaC templates, including SNS topics
9. ✅ **Write Unit Tests**: Created tests for the repository implementation
10. ✅ **Documentation**: Updated documentation with AWS-specific details

## 5. Terraform Resources (Implemented) ✅

The following AWS resources have been added to the Terraform IaC:

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

## 6. Next Steps

1. **Integration Testing**: Implement comprehensive integration tests for:
   - Replay API endpoints
   - Message publishing and consumption
   - Status tracking and reporting

2. **Schema Service Integration**: Finalize the integration with the Schema Service to retrieve all available kinds

3. **Monitoring Setup**: Set up CloudWatch dashboards and alarms for:
   - Message processing rates
   - Queue depths
   - Error rates
   - DynamoDB throttling events

4. **Documentation**: Update user documentation with instructions for using the replay feature

### 3.5 Schema Service Integration

To get a complete list of kinds, we integrated with the Schema Service:

```java
/**
 * Helper method to get all kinds from the schema service
 */
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

// Schema response classes
@Data
private static class SchemaInfoResponse {
    private List<SchemaInfo> schemaInfos;
}

@Data
private static class SchemaInfo {
    private SchemaIdentity schemaIdentity;
    private String createdBy;
    private String dateCreated;
    private String status;
    private String scope;
}

@Data
private static class SchemaIdentity {
    private String authority;
    private String source;
    private String entityType;
    private int schemaVersionMajor;
    private int schemaVersionMinor;
    private int schemaVersionPatch;
    private String id;
}
```

This approach ensures we have a complete list of kinds to query against, even if some kinds don't have any active records yet.
