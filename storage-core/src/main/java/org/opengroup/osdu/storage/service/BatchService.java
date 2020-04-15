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

package org.opengroup.osdu.storage.service;

import org.opengroup.osdu.core.common.model.storage.MultiRecordIds;
import org.opengroup.osdu.core.common.model.storage.MultiRecordInfo;
import org.opengroup.osdu.core.common.model.storage.MultiRecordRequest;
import org.opengroup.osdu.core.common.model.storage.MultiRecordResponse;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;

public interface BatchService {

	MultiRecordInfo getMultipleRecords(MultiRecordIds ids);

	MultiRecordResponse fetchMultipleRecords(MultiRecordRequest recordIds);

	DatastoreQueryResult getAllKinds(String cursor, Integer limit);

	DatastoreQueryResult getAllRecords(String cursorId, String kind, Integer limit);
}