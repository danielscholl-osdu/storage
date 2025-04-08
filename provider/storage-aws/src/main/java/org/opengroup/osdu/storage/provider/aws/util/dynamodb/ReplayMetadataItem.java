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

package org.opengroup.osdu.storage.provider.aws.util.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import lombok.Data;

import java.util.Date;

/**
 * DynamoDB model for storing replay status information.
 */
@Data
@DynamoDBTable(tableName = "ReplayStatus")
public class ReplayMetadataItem {
    
    /**
     * The ID of the replay item. For overall status, this is "overall".
     * For kind-specific status, this is the kind name.
     */
    @DynamoDBHashKey(attributeName = "id")
    private String id;
    
    /**
     * The unique identifier for the replay operation.
     */
    @DynamoDBRangeKey(attributeName = "replayId")
    @DynamoDBIndexHashKey(globalSecondaryIndexName = "ReplayIdIndex")
    private String replayId;
    
    /**
     * The kind of records being replayed. Only present for kind-specific status items.
     */
    @DynamoDBAttribute(attributeName = "kind")
    @DynamoDBIndexHashKey(globalSecondaryIndexName = "KindIndex")
    private String kind;
    
    /**
     * The operation being performed (e.g., "replay", "reindex").
     */
    @DynamoDBAttribute(attributeName = "operation")
    private String operation;
    
    /**
     * The total number of records to be processed.
     */
    @DynamoDBAttribute(attributeName = "totalRecords")
    private Long totalRecords;
    
    /**
     * The number of records that have been processed so far.
     */
    @DynamoDBAttribute(attributeName = "processedRecords")
    private Long processedRecords;
    
    /**
     * The current state of the replay operation (e.g., "QUEUED", "IN_PROGRESS", "COMPLETED", "FAILED").
     */
    @DynamoDBAttribute(attributeName = "state")
    private String state;
    
    /**
     * The timestamp when the replay operation started.
     */
    @DynamoDBAttribute(attributeName = "startedAt")
    private Date startedAt;
    
    /**
     * The elapsed time since the replay operation started.
     */
    @DynamoDBAttribute(attributeName = "elapsedTime")
    private String elapsedTime;
    
    /**
     * JSON serialized filter for the replay operation.
     */
    @DynamoDBAttribute(attributeName = "filter")
    private String filter;
    
    /**
     * The data partition ID for the replay operation.
     */
    @DynamoDBAttribute(attributeName = "dataPartitionId")
    private String dataPartitionId;
}
