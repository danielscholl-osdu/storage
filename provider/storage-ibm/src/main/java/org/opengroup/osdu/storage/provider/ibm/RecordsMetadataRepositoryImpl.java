/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.storage.provider.ibm;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.ibm.auth.ServiceCredentials;
import org.opengroup.osdu.core.ibm.cloudant.IBMCloudantClientFactory;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.cloudant.client.api.Database;
import com.cloudant.client.api.model.DesignDocument;
import com.cloudant.client.api.model.Params;
import com.cloudant.client.api.model.Response;
import com.cloudant.client.api.query.JsonIndex;
import com.cloudant.client.api.views.Key;
import com.cloudant.client.api.views.ViewResponse.Row;
import com.cloudant.client.org.lightcouch.NoDocumentException;

@Repository
public class RecordsMetadataRepositoryImpl implements IRecordsMetadataRepository<String> {

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
	
	@Value("${ibm.db.records.design-doc:records-ddoc}")
	private String ddoc;
	
	@Value("${ibm.db.records.view:records-view}")
	private String viewName;

	private IBMCloudantClientFactory cloudantFactory;

	private Database db;

	public final static String DB_NAME = "records";
	
	private static final Logger logger = LoggerFactory.getLogger(SchemaRepositoryImpl.class);

	@PostConstruct
	public void init() throws MalformedURLException {
		if (apiKey != null) {
			cloudantFactory = new IBMCloudantClientFactory(new ServiceCredentials(dbUrl, apiKey));
		} else {
			cloudantFactory = new IBMCloudantClientFactory(new ServiceCredentials(dbUrl, dbUser, dbPassword));
		}
		db = cloudantFactory.getDatabase(dbNamePrefix, DB_NAME);
		
		logger.info("creating indexes...");
		db.createIndex(JsonIndex.builder().name("kind-json-index").asc("kind").asc("_id").definition());
//		commenting below line as couch unable to create index on array 
//		db.createIndex(JsonIndex.builder().name("legalTagsNames-json-index").asc("legal.legaltags").asc("_id").definition());
		
		
		DesignDocument.MapReduce map = new DesignDocument.MapReduce();
		
		map.setMap("function(doc) { if(doc.legal.legaltags.length >0) { for(var idx in doc.legal.legaltags) { emit(doc.legal.legaltags[idx], null); } }}");
		Map<String, DesignDocument.MapReduce> views = new HashMap<String, DesignDocument.MapReduce>();
		views.put(viewName, map);
		
		DesignDocument designDocument = new DesignDocument();
		designDocument.setViews(views);
		designDocument.setId("_design/"+ddoc);
		
//		save method return 409 conflict if view exists
//		int statusCode = db.save(designDocument).getStatusCode();

		db.getDesignDocumentManager().put(designDocument);
		logger.info(viewName + " view created");
	}

	@Override
	public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata, Optional<CollaborationContext> collaborationContext) {
		
		List<RecordMetadata> resultList = new ArrayList<RecordMetadata>();

		if (recordsMetadata != null) {

			// map id with revs
            Map<String, String> idRevs = new HashMap<String, String>();

            for (RecordMetadata rm : recordsMetadata) {
            	try {
    				RecordMetadataDoc rmc = db.find(RecordMetadataDoc.class, rm.getId(), new Params().revsInfo());
    				idRevs.put(rm.getId(), rmc.getRev());
    			} catch (NoDocumentException e) {
    				// ignore
    			}
            }
            
			Date date = new Date();
			long now = date.getTime();

			List<RecordMetadataDoc> bulkList = new ArrayList<RecordMetadataDoc>();
			for (RecordMetadata rm : recordsMetadata) {
				RecordMetadataDoc rmd = new RecordMetadataDoc(rm);
				if (idRevs.containsKey(rmd.getId())) {
					rmd.setRev(idRevs.get(rmd.getId()));
					rmd.setModifyTime(now);
				} else {
					rmd.setCreateTime(now);
				}
				bulkList.add(rmd);
			}

			List<Response> bulkResponse = db.bulk(bulkList);
			for (Response response : bulkResponse) {
				RecordMetadataDoc rmdoc = new RecordMetadataDoc(response.getId(), response.getRev());
				resultList.add(rmdoc);
			}

		}
		return recordsMetadata;
	}

	@Override
	public void delete(String id, Optional<CollaborationContext> collaborationContext) {
		db.remove(db.find(RecordMetadataDoc.class, id));
	}

	@Override
	public RecordMetadata get(String id, Optional<CollaborationContext> collaborationContext) {
		try {
			RecordMetadataDoc rm = db.find(RecordMetadataDoc.class, id);
			return rm.getRecordMetadata();
		} catch (NoDocumentException e) {
			return null;
		}
	}

	@Override
	public Map<String, RecordMetadata> get(List<String> ids, Optional<CollaborationContext> collaborationContext) {
		Map<String, RecordMetadata> output = new HashMap<>();

		for (String id : ids) {
			try {
				RecordMetadataDoc rm = db.find(RecordMetadataDoc.class, id);
				output.put(rm.getId(), rm.getRecordMetadata());
			} catch (NoDocumentException e) {
				// ignore
			}
		}

		return output;
	}

	@Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
            String legalTagName[], int limit, String cursor) {
        throw new UnsupportedOperationException("Method not implemented.");
    }

	@Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
            String legalTagName, int limit, String cursor) {

		String initialId = QueryRepositoryImpl.validateCursor(cursor, db);

		int numRecords = QueryRepositoryImpl.PAGE_SIZE;
		if (Integer.valueOf(limit) != null) {
			numRecords = limit > 0 ? limit : QueryRepositoryImpl.PAGE_SIZE;
		}

		List<RecordMetadata> outputRecords = new ArrayList<>();
		long startTime = System.currentTimeMillis();
		List<Row<String, Object>> filteredRows = null;
		try {
			filteredRows = db.getViewRequestBuilder(ddoc, viewName).newRequest(Key.Type.STRING, Object.class)
					.keys(legalTagName).startKey(initialId).endKey(String.valueOf(numRecords)).build().getResponse().getRows();
		} catch (IOException e) {
			logger.error(e.getMessage());
			throw new AppException(500, e.getCause().toString(), e.getMessage());
		}

		logger.info("Time took to query view: "+ (System.currentTimeMillis() - startTime)+ "ms");
		
		String nextCursor = null;
		if (filteredRows != null && !filteredRows.isEmpty()) {
			for (Row<String, Object> row : filteredRows) {
				RecordMetadataDoc recordMetadataDoc = db.find(RecordMetadataDoc.class, row.getId());
				if (outputRecords.size() < numRecords) {
					outputRecords.add(recordMetadataDoc.getRecordMetadata());
				} else {
					nextCursor = recordMetadataDoc.getRecordMetadata().getId();
				}
			}
		}
		return new AbstractMap.SimpleEntry<>(nextCursor, outputRecords);
	}

	@Override
	public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit) {
		// TODO implement this!!!
		return null;
	}

}
