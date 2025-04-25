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

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;

import java.util.Date;

/**
 * AWS-specific extension of ReplayMetaDataDTO that includes additional fields
 * for supporting resume functionality.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AwsReplayMetaDataDTO extends ReplayMetaDataDTO {
    
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
    
    /**
     * Convert from a standard ReplayMetaDataDTO to an AWS-specific one
     * 
     * @param dto The standard ReplayMetaDataDTO
     * @return An AwsReplayMetaDataDTO with all fields copied from the original
     */
    public static AwsReplayMetaDataDTO fromReplayMetaDataDTO(ReplayMetaDataDTO dto) {
        AwsReplayMetaDataDTO awsDto = new AwsReplayMetaDataDTO();
        
        // Copy all fields from the base class
        awsDto.setId(dto.getId());
        awsDto.setReplayId(dto.getReplayId());
        awsDto.setKind(dto.getKind());
        awsDto.setOperation(dto.getOperation());
        awsDto.setTotalRecords(dto.getTotalRecords());
        awsDto.setProcessedRecords(dto.getProcessedRecords());
        awsDto.setState(dto.getState());
        awsDto.setStartedAt(dto.getStartedAt());
        awsDto.setElapsedTime(dto.getElapsedTime());
        awsDto.setFilter(dto.getFilter());
        
        return awsDto;
    }
    
    /**
     * Convert this AWS-specific DTO to a standard ReplayMetaDataDTO
     * 
     * @return A standard ReplayMetaDataDTO with all base fields copied
     */
    public ReplayMetaDataDTO toReplayMetaDataDTO() {
        ReplayMetaDataDTO dto = new ReplayMetaDataDTO();
        
        dto.setId(this.getId());
        dto.setReplayId(this.getReplayId());
        dto.setKind(this.getKind());
        dto.setOperation(this.getOperation());
        dto.setTotalRecords(this.getTotalRecords());
        dto.setProcessedRecords(this.getProcessedRecords());
        dto.setState(this.getState());
        dto.setStartedAt(this.getStartedAt());
        dto.setElapsedTime(this.getElapsedTime());
        dto.setFilter(this.getFilter());
        
        return dto;
    }
}
