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

import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.core.common.model.storage.validation.KindValidator;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.opengroup.osdu.core.common.model.http.AppException;
import java.util.*;

import static java.util.Collections.singletonList;

@Service
public class SchemaServiceImpl implements SchemaService {

	private static final String INVALID_SCHEMA_REASON = "Invalid schema";

	private static final Map<String, String> ALLOWED_TYPES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	static {
		ALLOWED_TYPES.put("integer", "int");
		ALLOWED_TYPES.put("int", "int");
		ALLOWED_TYPES.put("bool", "boolean");
		ALLOWED_TYPES.put("boolean", "boolean");
		ALLOWED_TYPES.put("float", "float");
		ALLOWED_TYPES.put("double", "double");
		ALLOWED_TYPES.put("long", "long");
		ALLOWED_TYPES.put("string", "string");
		ALLOWED_TYPES.put("link", "link");
		ALLOWED_TYPES.put("datetime", "datetime");
		ALLOWED_TYPES.put("core:dl:geopoint:1.0.0", "core:dl:geopoint:1.0.0");
		ALLOWED_TYPES.put("core:dl:geoshape:1.0.0", "core:dl:geoshape:1.0.0");
	}


	@Autowired
	private ISchemaRepository schemaRepository;

	@Autowired
	private ICache<String, Schema> cache;

	@Autowired
	private TenantInfo tenant;

	@Autowired
	private IMessageBus pubSubClient;

	@Autowired
	private DpsHeaders headers;

	@Autowired
	private StorageAuditLogger auditLogger;

	@Override
	public void createSchema(Schema inputSchema) {
		this.validateKindFromTenant(inputSchema.getKind());
		this.validateCircularReference(inputSchema, null);

		Schema schema = this.validateSchema(inputSchema);

		try {

			this.schemaRepository.add(schema, headers.getUserEmail());
			this.auditLogger.createSchemaSuccess(singletonList(inputSchema.getKind()));

			this.cache.put(this.getSchemaCacheKey(inputSchema.getKind()), schema);
			this.pubSubClient.publishMessage(this.headers,
					new PubSubInfo(null, inputSchema.getKind(), OperationType.create_schema));

		} catch (IllegalArgumentException e) {
			throw new AppException(HttpStatus.SC_CONFLICT, "Schema already registered",
					"The schema information for the given kind already exists.");
		} catch (ConcurrentModificationException e) {
			throw new AppException(HttpStatus.SC_CONFLICT, "Schema already registered",
					"Concurrent schema modification error.");
		} catch (Exception e) {
			throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error on schema creation",
					"An unknown error occurred during schema creation.");
		}
	}

	@Override
	public void deleteSchema(String kind) {

		this.validateKindFromTenant(kind);

		Schema schema = this.schemaRepository.get(kind);

		if (schema == null) {
			throw this.getSchemaNotFoundException(kind);
		}

		this.schemaRepository.delete(kind);
		this.auditLogger.deleteSchemaSuccess(singletonList(schema.getKind()));

		this.cache.delete(this.getSchemaCacheKey(kind));
		this.pubSubClient.publishMessage(this.headers,
				new PubSubInfo(null, schema.getKind(), OperationType.purge_schema));
	}

	@Override
	public Schema getSchema(String kind) {

		this.validateKindFromTenant(kind);

		Schema schema = this.fetchSchema(kind);

		if (schema == null) {
			throw this.getSchemaNotFoundException(kind);
		}

		return schema;
	}

	protected Schema validateSchema(Schema schema) {

		List<SchemaItem> items = new ArrayList<>();

		for (SchemaItem item : schema.getSchema()) {

			String array = "[]";
			String kind = null;
			Boolean isArray = false;
			String originalKind = item.getKind();
			String path = item.getPath();

			if (originalKind.contains(array)) {
				String head = originalKind.substring(0, 2);

				// Verify if the first two chars are []
				if (!head.equals(array)) {
					throw new AppException(HttpStatus.SC_BAD_REQUEST, INVALID_SCHEMA_REASON,
							String.format("Schema item invalid for path '%s': array types must start with '[]'", path));
				}

				kind = originalKind.substring(2).toLowerCase();
				isArray = true;
			} else {
				kind = originalKind.toLowerCase();
			}

			if (!ALLOWED_TYPES.containsKey(kind)) {
				throw new AppException(HttpStatus.SC_BAD_REQUEST, INVALID_SCHEMA_REASON,
						String.format("Schema item '%s' has an invalid data type '%s'", path, kind));
			}

			kind = ALLOWED_TYPES.get(kind);

			// Check if original kind is of type array then we need to add [] to front of
			// the kind
			if (isArray) {
				item.setKind(array + kind);
			} else {
				item.setKind(kind);
			}

			// Add updated item to the array list
			items.add(item);
		}

		return new Schema(schema.getKind(), items.toArray(new SchemaItem[items.size()]), schema.getExt());
	}

	private Schema fetchSchema(String kind) {

		String key = this.getSchemaCacheKey(kind);
		Schema cachedSchema = this.cache.get(key);

		if (cachedSchema == null) {
			Schema schema = this.schemaRepository.get(kind);
			this.auditLogger.readSchemaSuccess(singletonList(kind));

			if (schema == null) {
				return null;
			}

			this.cache.put(key, schema);

			return schema;
		} else {
			return cachedSchema;
		}
	}

	private void validateCircularReference(Schema schema, List<String> schemaList) {

		String kind = schema.getKind();

		if (schemaList == null) {
			schemaList = new ArrayList<>();
			schemaList.add(kind);
		}

		for (SchemaItem item : schema.getSchema()) {
			// Replace any array of type in the kind
			String itemKind = item.getKind().replace("[", "").replace("]", "");

			if (schemaList.contains(itemKind)) {
				throw new AppException(HttpStatus.SC_BAD_REQUEST, INVALID_SCHEMA_REASON, String.format(
						"Found circular reference kind: '%s' Schema list: %s", itemKind, schemaList.toString()));
			}
			// Recursively check if the kind points to another schema.
			if (itemKind.contains(":")) {
				Schema innerSchema = this.fetchSchema(itemKind);
				if (innerSchema != null) {
					schemaList.add(itemKind);
					this.validateCircularReference(innerSchema, schemaList);
				}
			}
		}
	}

	private void validateKindFromTenant(String kind) {
		String tenantName = this.tenant.getName();

		if (!KindValidator.isKindFromTenantValid(kind, tenantName)) {
			String msg = String.format("The kind '%s' does not belong to the account '%s'", kind, tenantName);

			throw new AppException(HttpStatus.SC_FORBIDDEN, "Invalid kind", msg);
		}
	}

	private String getSchemaCacheKey(String kind) {
		return Crc32c.hashToBase64EncodedString(String.format("schema:%s", kind));
	}

	private AppException getSchemaNotFoundException(String kind) {
		return new AppException(HttpStatus.SC_NOT_FOUND, "Schema not found",
				String.format("Schema not registered for kind '%s'", kind));
	}
}
