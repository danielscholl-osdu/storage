// Copyright 2017-2021, Schlumberger
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

package org.opengroup.osdu.storage.mapper;

import java.util.List;

import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.storage.response.CreateUpdateRecordsResponse;
import org.springframework.stereotype.Component;

@Component
public class CreateUpdateRecordsResponseMapper {

  public CreateUpdateRecordsResponse map(TransferInfo transferInfo, List<Record> records) {
    CreateUpdateRecordsResponse response = new CreateUpdateRecordsResponse();
    response.setRecordCount(transferInfo.getRecordCount());
    response.setSkippedRecordIds(transferInfo.getSkippedRecords());

    records.stream().filter(o -> !response.getSkippedRecordIds().contains(o.getId()))
        .forEach(r -> response.addRecord(r.getId(), transferInfo.getVersion()));

    return response;
  }

}
