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

import org.junit.Test;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.request.ReplayFilter;

import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.*;

public class AwsReplayMetaDataDTOTest {

    private static final String TEST_REPLAY_ID = "test-replay-id";
    private static final String TEST_KIND = "test-kind";
    private static final String TEST_OPERATION = "replay";
    private static final String TEST_CURSOR = "test-cursor";

    @Test
    public void testFromReplayMetaDataDTO() {
        // Create a standard DTO
        ReplayMetaDataDTO standardDto = createReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        
        // Convert to AWS DTO
        AwsReplayMetaDataDTO awsDto = AwsReplayMetaDataDTO.fromReplayMetaDataDTO(standardDto);
        
        // Verify all fields were copied correctly
        assertEquals(standardDto.getId(), awsDto.getId());
        assertEquals(standardDto.getReplayId(), awsDto.getReplayId());
        assertEquals(standardDto.getKind(), awsDto.getKind());
        assertEquals(standardDto.getOperation(), awsDto.getOperation());
        assertEquals(standardDto.getTotalRecords(), awsDto.getTotalRecords());
        assertEquals(standardDto.getProcessedRecords(), awsDto.getProcessedRecords());
        assertEquals(standardDto.getState(), awsDto.getState());
        assertEquals(standardDto.getStartedAt(), awsDto.getStartedAt());
        assertEquals(standardDto.getElapsedTime(), awsDto.getElapsedTime());
        assertEquals(standardDto.getFilter(), awsDto.getFilter());
        
        // AWS-specific fields should be null
        assertNull(awsDto.getLastCursor());
        assertNull(awsDto.getLastUpdatedAt());
    }
    
    @Test
    public void testToReplayMetaDataDTO() {
        // Create an AWS DTO with AWS-specific fields
        AwsReplayMetaDataDTO awsDto = createAwsReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        awsDto.setLastCursor(TEST_CURSOR);
        Date lastUpdated = new Date();
        awsDto.setLastUpdatedAt(lastUpdated);
        
        // Convert to standard DTO
        ReplayMetaDataDTO standardDto = awsDto.toReplayMetaDataDTO();
        
        // Verify all standard fields were copied correctly
        assertEquals(awsDto.getId(), standardDto.getId());
        assertEquals(awsDto.getReplayId(), standardDto.getReplayId());
        assertEquals(awsDto.getKind(), standardDto.getKind());
        assertEquals(awsDto.getOperation(), standardDto.getOperation());
        assertEquals(awsDto.getTotalRecords(), standardDto.getTotalRecords());
        assertEquals(awsDto.getProcessedRecords(), standardDto.getProcessedRecords());
        assertEquals(awsDto.getState(), standardDto.getState());
        assertEquals(awsDto.getStartedAt(), standardDto.getStartedAt());
        assertEquals(awsDto.getElapsedTime(), standardDto.getElapsedTime());
        assertEquals(awsDto.getFilter(), standardDto.getFilter());
        
        // AWS-specific fields should not be in the standard DTO
        // There's no direct way to check this since the fields don't exist in the standard DTO
    }
    
    @Test
    public void testAwsSpecificFields() {
        // Create an AWS DTO
        AwsReplayMetaDataDTO awsDto = new AwsReplayMetaDataDTO();
        
        // Set and verify AWS-specific fields
        String cursor = "test-cursor";
        Date lastUpdated = new Date();
        
        awsDto.setLastCursor(cursor);
        awsDto.setLastUpdatedAt(lastUpdated);
        
        assertEquals(cursor, awsDto.getLastCursor());
        assertEquals(lastUpdated, awsDto.getLastUpdatedAt());
    }
    
    private ReplayMetaDataDTO createReplayMetaData(String replayId, String kind, String operation) {
        ReplayMetaDataDTO dto = new ReplayMetaDataDTO();
        dto.setId(kind);
        dto.setReplayId(replayId);
        dto.setKind(kind);
        dto.setOperation(operation);
        dto.setState(ReplayState.QUEUED.name());
        dto.setStartedAt(new Date());
        dto.setTotalRecords(10L);
        dto.setProcessedRecords(0L);
        dto.setElapsedTime("00:10:00");
        
        // Add a filter
        ReplayFilter filter = new ReplayFilter();
        filter.setKinds(Collections.singletonList(kind));
        dto.setFilter(filter);
        
        return dto;
    }
    
    private AwsReplayMetaDataDTO createAwsReplayMetaData(String replayId, String kind, String operation) {
        AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
        dto.setId(kind);
        dto.setReplayId(replayId);
        dto.setKind(kind);
        dto.setOperation(operation);
        dto.setState(ReplayState.QUEUED.name());
        dto.setStartedAt(new Date());
        dto.setTotalRecords(10L);
        dto.setProcessedRecords(0L);
        dto.setElapsedTime("00:10:00");
        
        // Add a filter
        ReplayFilter filter = new ReplayFilter();
        filter.setKinds(Collections.singletonList(kind));
        dto.setFilter(filter);
        
        return dto;
    }
}
