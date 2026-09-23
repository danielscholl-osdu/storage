// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.storage.response;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Response object for record creation and update operations.")
public class CreateUpdateRecordsResponse {

  @Schema(description = "Number of records that were successfully created or updated.")
  private Integer recordCount;

  @Schema(description = "List of record ids that were successfully created or updated.")
  private List<String> recordIds;

  @Schema(description = "List of record ids that were skipped (e.g. duplicates).")
  private List<String> skippedRecordIds;

  @Schema(description = "List of record id:version pairs for the created or updated records.")
  private List<String> recordIdVersions;

  public void addRecord(String id, Long version) {
    addRecordIds(id);
    addRecordIdVersions(id, version);
  }

  private void addRecordIds(String recordId) {
    if (this.recordIds == null) {
      this.recordIds = new ArrayList<>();
    }
    this.recordIds.add(recordId);
  }

  private void addRecordIdVersions(String id, Long version) {
    if (this.recordIdVersions == null) {
      this.recordIdVersions = new ArrayList<>();
    }
    this.recordIdVersions.add(id + ':' + version);
  }
}
