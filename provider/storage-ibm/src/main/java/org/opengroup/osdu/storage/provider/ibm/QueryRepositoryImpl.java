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

import static com.cloudant.client.api.query.Expression.eq;
import static com.cloudant.client.api.query.Expression.gte;
import static com.cloudant.client.api.query.Operation.and;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.ibm.auth.ServiceCredentials;
import org.opengroup.osdu.core.ibm.cloudant.IBMCloudantClientFactory;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.cloudant.client.api.Database;
import com.cloudant.client.api.query.QueryBuilder;
import com.cloudant.client.api.query.QueryResult;
import com.cloudant.client.api.query.Sort;

@Repository
public class QueryRepositoryImpl implements IQueryRepository {
        
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
	
	private IBMCloudantClientFactory cloudantFactory;
	
	private Database dbSchema;
	private Database dbRecords;
	
	@PostConstruct
    public void init() throws MalformedURLException{
		
		if (apiKey != null) {
			cloudantFactory = new IBMCloudantClientFactory(new ServiceCredentials(dbUrl, apiKey));
		} else {
			cloudantFactory = new IBMCloudantClientFactory(new ServiceCredentials(dbUrl, dbUser, dbPassword));
		}
		
		dbSchema = cloudantFactory.getDatabase(dbNamePrefix, SchemaRepositoryImpl.SCHEMA_DATABASE);
        dbRecords = cloudantFactory.getDatabase(dbNamePrefix, RecordsMetadataRepositoryImpl.DB_NAME);
    }
	
    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {
        DatastoreQueryResult result = new DatastoreQueryResult();
        
        String initialId = validateCursor(cursor, dbSchema);
		
		int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }
                
		QueryResult<SchemaDoc> results = dbSchema.query(new QueryBuilder(
				   gte("_id", initialId)).
				   sort(Sort.asc("_id")).
				   fields("_id").
				   limit(numRecords+1).
				   build(), SchemaDoc.class);
		
		List<String> ids = new ArrayList<>();
		result.setCursor("");
		for (SchemaDoc doc:results.getDocs()) {
			if (ids.size() < numRecords) {
				ids.add(doc.getId());
			} else {
				// last record is the cursor
				result.setCursor(doc.getId());
			}
		}
		result.setResults(ids);
        
        return result;
    }

	@Override
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor) {
        
    	List<String> ids = new ArrayList<>();
        DatastoreQueryResult result = new DatastoreQueryResult();
        
        String initialId = validateCursor(cursor, dbRecords);
        
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }
                
        QueryResult<RecordMetadataDoc> results = dbRecords.query(new QueryBuilder(
        			and(eq("kind", kind), gte("_id", initialId))).
				    sort(Sort.asc("_id")).
				    fields("_id").
				    limit(numRecords+1).
				    build(), RecordMetadataDoc.class);
        
        
        result.setCursor("");
		for (RecordMetadataDoc doc:results.getDocs()) {
			if (ids.size() < numRecords) {
				ids.add(doc.getId());
			} else {
				// last record is the cursor
				result.setCursor(doc.getId());
			}
			
		}
        result.setResults(ids);
        
        return result;
    }
	
	public static String validateCursor(String cursor, Database db) {
    	if (cursor != null && !cursor.isEmpty()) {
    		if (db.contains(cursor)) {
    			return cursor;
    		} else {
				throw new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid",
		                "The requested cursor does not exist or is invalid");
			}
        } else {
        	return "0";
        }
	}

}

