/**
 * Copyright 2020 IBM Corp. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.ibm;

import java.net.MalformedURLException;

import javax.annotation.PostConstruct;

import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.ibm.auth.ServiceCredentials;
import org.opengroup.osdu.core.ibm.cloudant.IBMCloudantClientFactory;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.cloudant.client.api.Database;


@Repository
public class SchemaRepositoryImpl implements ISchemaRepository {

	@Value("${ibm.db.url}") 
	private String dbUrl;
	@Value("${ibm.db.apikey:#{null}}")
	private String apiKey;
	@Value("${ibm.db.user:#{null}}")
	private String dbUser;
	@Value("${ibm.db.password:#{null}}")
	private String dbPassword;
	@Value("${ibm.env.prefix:local-dev}")
	private String dbNamePrefix;
	
	private static final Logger logger = LoggerFactory.getLogger(SchemaRepositoryImpl.class);
	
	private IBMCloudantClientFactory cloudantFactory;
	
	private Database db;

	public static final String SCHEMA_DATABASE = "schema"; 

    @PostConstruct
    public void init() throws MalformedURLException {
    	if (apiKey != null) {
			cloudantFactory = new IBMCloudantClientFactory(new ServiceCredentials(dbUrl, apiKey));
		} else {
			cloudantFactory = new IBMCloudantClientFactory(new ServiceCredentials(dbUrl, dbUser, dbPassword));
		}
        db = cloudantFactory.getDatabase(dbNamePrefix, SCHEMA_DATABASE);
    }
    
	@Override
	public void add(Schema schema, String user) {
		String kind = schema.getKind();
		if (db.contains(kind)) {
			logger.error("Schema " + kind + " already exist. Can't create again.");
			throw new IllegalArgumentException("Schema " + kind + " already exist. Can't create again.");
		}
		SchemaDoc sd = new SchemaDoc(schema, user);
		db.save(sd);
	}

	@Override
	public Schema get(String kind) {
		if (db.contains(kind)) {
			SchemaDoc sd = db.find(SchemaDoc.class, kind);
			Schema newSchema = new Schema();
			newSchema.setKind(kind);
			newSchema.setSchema(sd.getSchema());
			newSchema.setExt(sd.getExtension());
			return newSchema;
		} else {
			return null;
		}
	}

	@Override
	public void delete(String kind) {
		db.remove(db.find(SchemaDoc.class, kind));
	}

}
