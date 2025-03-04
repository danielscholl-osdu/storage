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

package org.opengroup.osdu.storage.provider.interfaces;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.HashMap;
import java.util.List;

import java.util.Map;
import java.util.Optional;

public interface IQueryRepository {

	static final int PAGE_SIZE = 1000;

	DatastoreQueryResult getAllKinds(Integer limit, String cursor);

	DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor, Optional<CollaborationContext> collaborationContext);

	RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor);

	RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind);

	HashMap<String, Long> getActiveRecordsCount();

	Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds);
}
