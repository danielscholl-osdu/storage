/*
 * Copyright 2021 Google LLC
 * Copyright 2021 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Cursor;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.FullEntity;
import com.google.cloud.datastore.FullEntity.Builder;
import com.google.cloud.datastore.IncompleteKey;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StringValue;
import com.google.cloud.datastore.StructuredQuery;
import com.google.cloud.datastore.Value;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.jsonwebtoken.lang.Collections;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordAncestry;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.gcp.multitenancy.IDatastoreFactory;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
@Log
public class DatastoreRecordsMetadataRepository implements IRecordsMetadataRepository<Cursor> {

	public static final String RECORD_KIND = "StorageRecord";

	public static final String ACL = "acl";
	public static final String ACL_VIEWERS = "viewers";
	public static final String ACL_OWNERS = "owners";
	public static final String BUCKET = "bucket";
	public static final String CREATE_TIME = "createTime";
	public static final String CREATE_USER = "createUser";
	public static final String KIND = "kind";
	public static final String MODIFY_TIME = "modifyTime";
	public static final String MODIFY_USER = "modifyUser";
	public static final String VERSION = "version";
	public static final String STATUS = "status";

	public static final String LEGAL = "legal";
	public static final String LEGAL_TAGS = "legaltags";
	public static final String LEGAL_ORDC = "otherRelevantDataCountries";
	public static final String LEGAL_COMPLIANCE = "status";

	public static final String ANCESTRY = "ancestry";
	public static final String ANCESTRY_PARENTS = "parents";
	public static final String TAGS = "tags";

	@Autowired
	private IDatastoreFactory datastoreFactory;

	@Override
	public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata) {
		if (recordsMetadata != null) {
			List<FullEntity<Key>> recordEntities = new ArrayList<>(recordsMetadata.size());
			for (RecordMetadata recordMetadata : recordsMetadata) {
				FullEntity<Key> recordEntity = this.createRecordEntity(recordMetadata);
				recordEntities.add(recordEntity);
			}
			this.addRecords(recordEntities);
		}
		return recordsMetadata;
	}

	@Override
	public void delete(String id) {
		Key key = this.getRecordKey(id);
		this.datastoreFactory.getDatastore().delete(key);
	}

	@Override
	public RecordMetadata get(String id) {
		Key key = this.getRecordKey(id);
		Entity entity = this.datastoreFactory.getDatastore().get(key);
		return this.parseEntityToRecordMetadata(entity);
	}

	@Override
	public AbstractMap.SimpleEntry<Cursor, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit) {

		StructuredQuery.PropertyFilter legalNameFilter = StructuredQuery.PropertyFilter.eq(String.format("%s.%s", LEGAL, LEGAL_TAGS), legalTagName);
		StructuredQuery.PropertyFilter legalStatusFilter = StructuredQuery.PropertyFilter.eq(String.format("%s.%s", LEGAL, LEGAL_COMPLIANCE), status.name());

		StructuredQuery.CompositeFilter filter = StructuredQuery.CompositeFilter.and(legalNameFilter, legalStatusFilter);

		Query<Entity> query = Query.newEntityQueryBuilder()
				.setKind(RECORD_KIND)
				.setFilter(filter)
				.setLimit(limit)
				.build();

		QueryResults<Entity> results = this.datastoreFactory.getDatastore().run(query);
		List<RecordMetadata> outputRecords = new ArrayList<>();

		while (results.hasNext()) {
			RecordMetadata recordMetadata = this.parseEntityToRecordMetadata(results.next());
			outputRecords.add(recordMetadata);
		}

		return new AbstractMap.SimpleEntry<>(results.getCursorAfter(), outputRecords);
	}

	@Override
	public Map<String, RecordMetadata> get(List<String> ids) {
		Key[] keys = new Key[ids.size()];

		for (int i = 0; i < ids.size(); i++) {
			keys[i] = this.getRecordKey(ids.get(i));
		}

		Map<String, RecordMetadata> output = new HashMap<>();

		Iterator<Entity> entities = this.datastoreFactory.getDatastore().get(keys);

		while (entities.hasNext()) {
			Entity entity = entities.next();

			if (entity != null) {
				output.put(entity.getKey().getName(), this.parseEntityToRecordMetadata(entity));
			}
		}

		return output;
	}

	//TODO remove when other providers replace with new method queryByLegal
	@Override
	public AbstractMap.SimpleEntry<Cursor, List<RecordMetadata>> queryByLegalTagName(String legalTagName, int limit, Cursor cursor) {
		return null;
	}

	private RecordMetadata parseEntityToRecordMetadata(Entity entity) {
		RecordMetadata recordMetadata = null;
		if (entity != null) {
			recordMetadata = new RecordMetadata();
			recordMetadata.setId(entity.getKey().getName());
			recordMetadata.setGcsVersionPaths(buildListObjectArray(entity.getList(BUCKET)));

			recordMetadata.setKind(entity.getString(KIND));
			recordMetadata.setStatus(RecordState.valueOf(entity.getString(STATUS)));
			recordMetadata.setUser(entity.getString(CREATE_USER));
			recordMetadata.setCreateTime(TimeUnit.SECONDS.toMillis(entity.getTimestamp(CREATE_TIME).getSeconds())
					+ TimeUnit.NANOSECONDS.toMillis(entity.getTimestamp(CREATE_TIME).getNanos()));
			recordMetadata.setAcl(this.buildAclObject(entity.getEntity(ACL)));
			recordMetadata.setLegal(this.buildLegalObject(entity));

			if (entity.contains(ANCESTRY)) {
				RecordAncestry ancestry = new RecordAncestry();
				ancestry.setParents(this.buildObjectSet(entity.getEntity(ANCESTRY).getList(ANCESTRY_PARENTS)));

				recordMetadata.setAncestry(ancestry);
			}

			if (entity.contains(MODIFY_USER)) {
				recordMetadata.setModifyUser(entity.getString(MODIFY_USER));
			}

			if (entity.contains(TAGS)) {
				String tags = entity.getString(TAGS);
				if (!tags.isEmpty()) {
					Map tagsMap = new Gson().fromJson(tags, Map.class);
					recordMetadata.setTags(tagsMap);
				}
			}

			if (entity.contains(MODIFY_TIME)) {
				recordMetadata.setModifyTime(TimeUnit.SECONDS.toMillis(entity.getTimestamp(MODIFY_TIME).getSeconds())
						+ TimeUnit.NANOSECONDS.toMillis(entity.getTimestamp(MODIFY_TIME).getNanos()));
			}
		}
		return recordMetadata;
	}

	private Key getRecordKey(String id) {
		return this.datastoreFactory.getDatastore().newKeyFactory().setKind(RECORD_KIND).newKey(id);
	}

	@SuppressWarnings("unchecked")
	private List<Entity> addRecords(List<FullEntity<Key>> entities) {
		FullEntity<Key>[] entityArray = entities.toArray(new FullEntity[entities.size()]);
		return this.datastoreFactory.getDatastore().put(entityArray);
	}

	private FullEntity<Key> createRecordEntity(RecordMetadata record) {
		Key key = this.getRecordKey(record.getId());

		Builder<Key> entityBuilder = FullEntity.newBuilder(key)
				.set(BUCKET,
						this.buildEntityArray(
								record.getGcsVersionPaths().toArray(new String[record.getGcsVersionPaths().size()])))
				.set(KIND, record.getKind())
				.set(ACL, this.buildAclEntity(record.getAcl()))
				.set(STATUS, record.getStatus().toString())
				.set(CREATE_USER, record.getUser())
				.set(CREATE_TIME, Timestamp.ofTimeMicroseconds(TimeUnit.MILLISECONDS.toMicros(record.getCreateTime())))
				.set(LEGAL, this.buildLegalEntity(record.getLegal()));

		if (record.getAncestry() != null && !Collections.isEmpty(record.getAncestry().getParents())) {
			entityBuilder.set(ANCESTRY, FullEntity.newBuilder()
					.set(ANCESTRY_PARENTS, this.buildEntityArray(record.getAncestry().getParents())).build());
		}

		log.info(String.format("Record Tags = %s", record.getTags()));
		if (record.getTags() != null && !record.getTags().isEmpty()) {
			try {
				entityBuilder.set(TAGS, new ObjectMapper().writeValueAsString(record.getTags()));
			} catch (JsonProcessingException e) {
				log.warning(e.getMessage());
			}
		}

		if (record.getModifyTime() > 0) {
			entityBuilder.set(MODIFY_TIME, Timestamp.ofTimeMicroseconds(TimeUnit.MILLISECONDS.toMicros(record.getModifyTime())));
		}

		if (!Strings.isNullOrEmpty(record.getModifyUser())) {
			entityBuilder.set(MODIFY_USER, record.getModifyUser());
		}

		return entityBuilder.build();
	}

	private Acl buildAclObject(FullEntity<IncompleteKey> entity) {

		Acl acl = new Acl();
		acl.setViewers(this.buildObjectArray(entity.getList(ACL_VIEWERS)));
		acl.setOwners(this.buildObjectArray(entity.getList(ACL_OWNERS)));

		return acl;
	}

	private Legal buildLegalObject(Entity entity) {

		Legal legal = new Legal();
		legal.setLegaltags(this.buildObjectSet(entity.getEntity(LEGAL).getList(LEGAL_TAGS)));
		legal.setOtherRelevantDataCountries(this.buildObjectSet(entity.getEntity(LEGAL).getList(LEGAL_ORDC)));
		legal.setStatus(LegalCompliance.valueOf(entity.getEntity(LEGAL).getString(LEGAL_COMPLIANCE)));

		return legal;
	}

	private FullEntity<IncompleteKey> buildAclEntity(Acl acl) {
		return FullEntity.newBuilder().set(ACL_VIEWERS, this.buildEntityArray(acl.getViewers()))
				.set(ACL_OWNERS, this.buildEntityArray(acl.getOwners())).build();
	}

	private FullEntity<IncompleteKey> buildLegalEntity(Legal legal) {

		return FullEntity.newBuilder().set(LEGAL_TAGS, this.buildEntityArray(legal.getLegaltags()))
				.set(LEGAL_ORDC, this.buildEntityArray(legal.getOtherRelevantDataCountries()))
				.set(LEGAL_COMPLIANCE, legal.getStatus().toString()).build();

	}

	private String[] buildObjectArray(List<Value<String>> list) {
		String[] array = new String[list.size()];

		for (int i = 0; i < list.size(); i++) {
			array[i] = list.get(i).get();
		}

		return array;
	}

	private Set<String> buildObjectSet(List<Value<String>> list) {
		Set<String> set = new HashSet<>();

		for (Value<String> item : list) {
			set.add(item.get());
		}

		return set;
	}

	private List<StringValue> buildEntityArray(String[] arrayValues) {
		List<StringValue> values = new ArrayList<>();

		for (String value : arrayValues) {
			values.add(new StringValue(value));
		}

		return values;
	}

	private List<StringValue> buildEntityArray(Set<String> setValues) {
		List<StringValue> values = new ArrayList<>();

		for (String value : setValues) {
			values.add(new StringValue(value));
		}

		return values;
	}

	private static List<String> buildListObjectArray(List<Value<String>> list) {
		List<String> values = new ArrayList<>(list.size());

		for (int i = 0; i < list.size(); i++) {
			values.add(list.get(i).get());
		}

		return values;
	}
}