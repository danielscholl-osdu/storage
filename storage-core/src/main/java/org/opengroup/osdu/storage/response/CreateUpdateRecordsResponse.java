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

import lombok.Data;

@Data
public class CreateUpdateRecordsResponse {

  private Integer recordCount;

  private List<String> recordIds;

  private List<String> skippedRecordIds;

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
