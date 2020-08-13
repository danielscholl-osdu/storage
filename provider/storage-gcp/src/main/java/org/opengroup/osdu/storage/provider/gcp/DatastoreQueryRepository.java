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

package org.opengroup.osdu.storage.provider.gcp;

import java.util.ArrayList;
import java.util.List;

import org.opengroup.osdu.core.gcp.multitenancy.IDatastoreFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.google.common.base.Strings;
import com.google.cloud.datastore.Cursor;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.ProjectionEntity;
import com.google.cloud.datastore.ProjectionEntityQuery;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;

@Repository
public class DatastoreQueryRepository implements IQueryRepository {

	@Autowired
	private IDatastoreFactory datastoreFactory;

	@Override
	public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {

		ProjectionEntityQuery query;

		int numRecords;
		if (limit == null) {
			numRecords = PAGE_SIZE;
		} else {
			numRecords = limit > 0 ? limit : PAGE_SIZE;
		}

		if (!Strings.isNullOrEmpty(cursor)) {
			Cursor startCursor = Cursor.fromUrlSafe(cursor);

			query = Query.newProjectionEntityQueryBuilder().setKind(DatastoreRecordsMetadataRepository.RECORD_KIND)
					.setProjection(DatastoreRecordsMetadataRepository.KIND)
					.setFilter(
							PropertyFilter.eq(DatastoreRecordsMetadataRepository.STATUS, RecordState.active.toString()))
					.setDistinctOn(DatastoreRecordsMetadataRepository.KIND).setLimit(numRecords)
					.setStartCursor(startCursor).setOrderBy(OrderBy.asc(DatastoreRecordsMetadataRepository.KIND))
					.build();
		} else {
			query = Query.newProjectionEntityQueryBuilder().setKind(DatastoreRecordsMetadataRepository.RECORD_KIND)
					.setProjection(DatastoreRecordsMetadataRepository.KIND)
					.setFilter(
							PropertyFilter.eq(DatastoreRecordsMetadataRepository.STATUS, RecordState.active.toString()))
					.setDistinctOn(DatastoreRecordsMetadataRepository.KIND).setLimit(numRecords)
					.setOrderBy(OrderBy.asc(DatastoreRecordsMetadataRepository.KIND)).build();
		}

		QueryResults<ProjectionEntity> results = this.datastoreFactory.getDatastore().run(query);

		List<String> kinds = new ArrayList<>();

		results.forEachRemaining(r -> kinds.add(r.getString(DatastoreRecordsMetadataRepository.KIND)));

		return new DatastoreQueryResult(results.getCursorAfter().toUrlSafe(), kinds);
	}

	@Override
	public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor) {

		int numRecords = PAGE_SIZE;
		if (limit != null) {
			numRecords = limit > 0 ? limit : PAGE_SIZE;
		}

		Query<Key> query;

		PropertyFilter kindFilter = PropertyFilter.eq(DatastoreRecordsMetadataRepository.KIND, kind);
		PropertyFilter activeFilter = PropertyFilter.eq(DatastoreRecordsMetadataRepository.STATUS,
				RecordState.active.toString());

		CompositeFilter filter = CompositeFilter.and(kindFilter, activeFilter);

		if (!Strings.isNullOrEmpty(cursor)) {
			Cursor startCursor = Cursor.fromUrlSafe(cursor);

			query = Query.newKeyQueryBuilder().setKind(DatastoreRecordsMetadataRepository.RECORD_KIND)
					.setFilter(filter)
					.setLimit(numRecords)
					.setStartCursor(startCursor).build();
		} else {
			query = Query.newKeyQueryBuilder().setKind(DatastoreRecordsMetadataRepository.RECORD_KIND)
					.setFilter(filter)
					.setLimit(numRecords)
					.build();
		}

		QueryResults<Key> results = this.datastoreFactory.getDatastore().run(query);

		List<String> ids = new ArrayList<>();

		results.forEachRemaining(r -> ids.add(r.getNameOrId().toString()));

		return new DatastoreQueryResult(results.getCursorAfter().toUrlSafe(), ids);
	}
}