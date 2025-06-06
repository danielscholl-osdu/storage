/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.storage.provider.ibm;

import static com.cloudant.client.api.query.Expression.eq;
import static com.cloudant.client.api.query.Expression.gte;
import static com.cloudant.client.api.query.Operation.and;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.ibm.auth.ServiceCredentials;
import org.opengroup.osdu.core.ibm.cloudant.IBMCloudantClientFactory;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
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
	
	@Inject
	private TenantInfo tenant;
	
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
		try {
			tenant.getName();
		} catch (Exception e) {
			throw new AppException(HttpStatus.SC_UNAUTHORIZED, "not authorized", "not authorized");
		}

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
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor, Optional<CollaborationContext> collaborationContext) {
        
    	List<String> ids = new ArrayList<>();
        DatastoreQueryResult result = new DatastoreQueryResult();
        
        String initialId = validateCursor(cursor, dbRecords);
        
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }
                
        QueryResult<RecordMetadataDoc> results = dbRecords.query(new QueryBuilder(
        			and(eq("kind", kind), gte("_id", initialId), eq("status",RecordState.active.toString()))).
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

	@Override
	public RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor) {
		throw new NotImplementedException();
	}

	@Override
	public RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind) {
		throw new  NotImplementedException();
	}

	@Override
	public HashMap<String, Long> getActiveRecordsCount() {
		throw new  NotImplementedException();
	}

	@Override
	public Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds) {
		throw new  NotImplementedException();
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

