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

import static com.cloudant.client.api.query.Expression.gte;
import static com.cloudant.client.api.query.PredicatedOperation.elemMatch;
import static com.cloudant.client.api.query.PredicateExpression.eq;
import static com.cloudant.client.api.query.Operation.and;

import java.net.MalformedURLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.ibm.auth.ServiceCredentials;
import org.opengroup.osdu.core.ibm.cloudant.IBMCloudantClientFactory;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.cloudant.client.api.Database;
import com.cloudant.client.api.model.Params;
import com.cloudant.client.api.model.Response;
import com.cloudant.client.api.query.JsonIndex;
import com.cloudant.client.api.query.QueryBuilder;
import com.cloudant.client.api.query.QueryResult;
import com.cloudant.client.api.query.Sort;
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

	private IBMCloudantClientFactory cloudantFactory;

	private Database db;

	public final static String DB_NAME = "records";

	@PostConstruct
	public void init() throws MalformedURLException {
		if (apiKey != null) {
			cloudantFactory = new IBMCloudantClientFactory(new ServiceCredentials(dbUrl, apiKey));
		} else {
			cloudantFactory = new IBMCloudantClientFactory(new ServiceCredentials(dbUrl, dbUser, dbPassword));
		}
		db = cloudantFactory.getDatabase(dbNamePrefix, DB_NAME);
		System.out.println("creating indexes...");
		db.createIndex(JsonIndex.builder().name("kind-json-index").asc("kind").asc("_id").definition());
		db.createIndex(JsonIndex.builder().name("legalTagsNames-json-index").asc("legal.legaltags").asc("_id").definition());
	}

	@Override
	public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata) {

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
	public void delete(String id) {
		db.remove(db.find(RecordMetadataDoc.class, id));
	}

	@Override
	public RecordMetadata get(String id) {
		try {
			RecordMetadataDoc rm = db.find(RecordMetadataDoc.class, id);
			return rm.getRecordMetadata();
		} catch (NoDocumentException e) {
			return null;
		}
	}

	@Override
	public Map<String, RecordMetadata> get(List<String> ids) {
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
            String legalTagName, int limit, String cursor) {

		String initialId = QueryRepositoryImpl.validateCursor(cursor, db);

		int numRecords = QueryRepositoryImpl.PAGE_SIZE;
		if (Integer.valueOf(limit) != null) {
			numRecords = limit > 0 ? limit : QueryRepositoryImpl.PAGE_SIZE;
		}

		List<RecordMetadata> outputRecords = new ArrayList<>();

		QueryResult<RecordMetadataDoc> results = db
				.query(new QueryBuilder(and(elemMatch("legal.legaltags", eq(legalTagName)), gte("_id", initialId)))
						.sort(Sort.asc("_id")).fields("_id", "legal", "gcsVersionPaths").limit(numRecords + 1).build(), RecordMetadataDoc.class);

		String nextCursor = null;
		for (RecordMetadataDoc doc : results.getDocs()) {
			if (outputRecords.size() < numRecords) {
				outputRecords.add(doc.getRecordMetadata());
			} else {
				nextCursor = doc.getRecordMetadata().getId();
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
