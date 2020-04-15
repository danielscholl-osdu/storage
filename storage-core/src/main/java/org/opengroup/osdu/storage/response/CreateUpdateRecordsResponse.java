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

import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;

import lombok.Data;

@Data
public class CreateUpdateRecordsResponse {

	private Integer recordCount;

	private List<String> recordIds;

	private List<String> skippedRecordIds;

	public CreateUpdateRecordsResponse(TransferInfo transferInfo, List<Record> records) {
		this.recordCount = transferInfo.getRecordCount();
		this.skippedRecordIds = transferInfo.getSkippedRecords();
		this.recordIds = new ArrayList<>();

		records.forEach(r -> this.recordIds.add(r.getId()));
		this.recordIds.removeAll(this.skippedRecordIds);
	}
}