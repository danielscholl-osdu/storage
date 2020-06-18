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

import java.lang.reflect.Type;
import java.util.Map;

import org.opengroup.osdu.storage.provider.gcp.credentials.IDatastoreFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.BlobValue;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.FullEntity;
import com.google.cloud.datastore.FullEntity.Builder;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Transaction;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;

import io.jsonwebtoken.lang.Collections;

@Repository
public class DatastoreSchemaRepository implements ISchemaRepository {

	@Autowired
	private IDatastoreFactory datastoreFactory;

	@Override
	public void add(Schema schema, String user) {
		Datastore datastore = this.datastoreFactory.getDatastore();
		Key schemaKey = datastore.newKeyFactory().setKind(SCHEMA_KIND).newKey(schema.getKind());
		Transaction txn = datastore.newTransaction();

		if (txn.get(schemaKey) != null) {
           try {
			throw new IllegalArgumentException("A schema for the specified kind has already been registered.");
		   } finally {
			   if (txn.isActive()) {
				   txn.rollback();
			   }
		   }
		} else {
			try {

				Gson gson = new Gson();

				Blob schemaBlob = Blob.copyFrom(gson.toJson(schema.getSchema()).getBytes());

				Builder<Key> builder = FullEntity.newBuilder(schemaKey);
				builder.set(USER, user);
				builder.set(SCHEMA, BlobValue.newBuilder(schemaBlob).setExcludeFromIndexes(true).build());

				if (!Collections.isEmpty(schema.getExt())) {
					Blob extBlob = Blob.copyFrom(gson.toJson(schema.getExt()).getBytes());
					builder.set(EXTENSION, BlobValue.newBuilder(extBlob).setExcludeFromIndexes(true).build());
				}

				FullEntity<Key> schemaEntity = builder.build();

				txn.put(schemaEntity);

				txn.commit();

			} finally {
				if (txn.isActive()) {
					txn.rollback();
				}
			}
		}
	}

	@Override
	public Schema get(String kind) {
		Datastore datastore = this.datastoreFactory.getDatastore();
		Key schemaKey = datastore.newKeyFactory().setKind(SCHEMA_KIND).newKey(kind);
		Entity schemaEntity = datastore.get(schemaKey);

		if (schemaEntity != null) {
			Gson gson = new Gson();

			Blob schemaBlob = schemaEntity.getBlob(SCHEMA);
			String schemaString = new String(schemaBlob.toByteArray());

			SchemaItem[] schemaItems = gson.fromJson(schemaString, SchemaItem[].class);

			Map<String, Object> ext = null;

			if (schemaEntity.contains(EXTENSION)) {
				Blob extBlob = schemaEntity.getBlob(EXTENSION);
				String extString = new String(extBlob.toByteArray());

				Type mapType = new TypeToken<Map<String, Object>>() {
				}.getType();

				ext = gson.fromJson(extString, mapType);
			}

			return new Schema(kind, schemaItems, ext);

		} else {
			return null;
		}
	}

	@Override
	public void delete(String kind) {
		Datastore datastore = this.datastoreFactory.getDatastore();
		Key key = datastore.newKeyFactory().setKind(SCHEMA_KIND).newKey(kind);
		datastore.delete(key);
	}
}