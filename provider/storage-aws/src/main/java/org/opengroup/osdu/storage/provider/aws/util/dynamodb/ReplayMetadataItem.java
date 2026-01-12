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

package org.opengroup.osdu.storage.provider.aws.util.dynamodb;

import org.opengroup.osdu.storage.provider.aws.util.dynamodb.converters.DateTypeConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import lombok.Data;

import java.util.Date;

/**
 * DynamoDB model for storing replay status information.
 */
@Data
@DynamoDbBean
public class ReplayMetadataItem {
    
    /**
     * The ID of the replay item. For overall status, this is "overall".
     * For kind-specific status, this is the kind name.
     */
    private String id;
    
    /**
     * The unique identifier for the replay operation.
     */
    private String replayId;
    
    /**
     * The kind of records being replayed. Only present for kind-specific status items.
     */
    private String kind;
    
    /**
     * The operation being performed (e.g., "replay", "reindex").
     */
    private String operation;
    
    /**
     * The total number of records to be processed.
     */
    private Long totalRecords;
    
    /**
     * The number of records that have been processed so far.
     */
    private Long processedRecords;
    
    /**
     * The current state of the replay operation (e.g., "QUEUED", "IN_PROGRESS", "COMPLETED", "FAILED").
     */
    private String state;
    
    /**
     * The timestamp when the replay operation started.
     */
    private Date startedAt;
    
    /**
     * The elapsed time since the replay operation started.
     */
    private String elapsedTime;
    
    /**
     * JSON serialized filter for the replay operation.
     */
    private String filter;
    
    /**
     * The data partition ID for the replay operation.
     */
    private String dataPartitionId;
    
    /**
     * The cursor position for resuming processing if interrupted.
     * This allows a new job to pick up where the previous one left off.
     */
    private String lastCursor;
    
    /**
     * The timestamp when the last batch was processed.
     * Used to detect stalled jobs and for monitoring.
     */
    private Date lastUpdatedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setKId(String id) {
        this.id = id;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"ReplayIdIndex"})
    @DynamoDbSortKey
    @DynamoDbAttribute("replayId")
    public String getReplayId() {
        return replayId;
    }

    public void setReplayId(String replayId) {
        this.replayId = replayId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"KindIndex"})
    @DynamoDbAttribute("kind")
    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    @DynamoDbAttribute("operation")
    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    @DynamoDbAttribute("totalRecords")
    public Long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(Long totalRecords) {
        this.totalRecords = totalRecords;
    }

    @DynamoDbAttribute("processedRecords")
    public Long getProcessedRecords() {
        return processedRecords;
    }

    public void setProcessedRecords(Long processedRecords) {
        this.processedRecords = processedRecords;
    }

    @DynamoDbAttribute("state")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @DynamoDbConvertedBy(DateTypeConverter.class)
    @DynamoDbAttribute("startedAt")
    public Date getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Date startedAt) {
        this.startedAt = startedAt;
    }

    @DynamoDbAttribute("elapsedTime")
    public String getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(String elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    @DynamoDbAttribute("filter")
    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    @DynamoDbAttribute("dataPartitionId")
    public String getDataPartitionId() {
        return dataPartitionId;
    }

    public void setDataPartitionId(String dataPartitionId) {
        this.dataPartitionId = dataPartitionId;
    }

    @DynamoDbAttribute("lastCursor")
    public String getLastCursor() {
        return lastCursor;
    }

    public void setLastCursor(String lastCursor) {
        this.lastCursor = lastCursor;
    }

    @DynamoDbConvertedBy(DateTypeConverter.class)
    @DynamoDbAttribute("lastUpdatedAt")
    public Date getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Date lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }
}
