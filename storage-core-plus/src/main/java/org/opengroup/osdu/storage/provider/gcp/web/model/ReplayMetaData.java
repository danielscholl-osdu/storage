/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.web.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.storage.request.ReplayFilter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayMetaData {
  private String id;

  private String replayId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String kind;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String operation;

  private Long totalRecords;

  private Date startedAt;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private ReplayFilter filter;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long processedRecords;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String state;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String elapsedTime;
}
