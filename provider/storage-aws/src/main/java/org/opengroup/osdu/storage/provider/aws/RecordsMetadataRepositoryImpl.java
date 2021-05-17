// Copyright © 2020 Amazon Web Services
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

package org.opengroup.osdu.storage.provider.aws;

import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.provider.aws.security.UserAccessService;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.LegalTagAssociationDoc;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.util.*;

@Repository
public class RecordsMetadataRepositoryImpl implements IRecordsMetadataRepository<String> {  

    @Inject
    private JaxRsDpsLog logger;

    @Inject
    private UserAccessService userAccessService;

    @Inject
    private DpsHeaders headers;

    @Inject
    private DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Value("${aws.dynamodb.recordMetadataTable.ssm.relativePath}")
    String recordMetadataTableParameterRelativePath;

    @Value("${aws.dynamodb.legalTagTable.ssm.relativePath}")
    String legalTagTableParameterRelativePath;

    private DynamoDBQueryHelperV2 getRecordMetadataQueryHelper() {
        return dynamoDBQueryHelperFactory.getQueryHelperForPartition(headers, recordMetadataTableParameterRelativePath);
    }

    private DynamoDBQueryHelperV2 getLegalTagQueryHelper() {
        return dynamoDBQueryHelperFactory.getQueryHelperForPartition(headers, legalTagTableParameterRelativePath);
    }

    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata) {
        if (recordsMetadata != null) {

            DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();

            for (RecordMetadata recordMetadata : recordsMetadata) {
                // user should be part of the acl of the record being saved
                RecordMetadataDoc doc = new RecordMetadataDoc();

                // Set the core fields (what is expected in every implementation)
                doc.setId(recordMetadata.getId());
                doc.setMetadata(recordMetadata);

                // Add extra indexed fields for querying in DynamoDB
                doc.setKind(recordMetadata.getKind());
                doc.setLegaltags(recordMetadata.getLegal().getLegaltags());
                doc.setStatus(recordMetadata.getStatus().name());
                doc.setUser(recordMetadata.getUser());

                // Store the record to the database
                recordMetadataQueryHelper.save(doc);
                saveLegalTagAssociation(recordMetadata.getId(), recordMetadata.getLegal().getLegaltags());
            }
        }
        return recordsMetadata;
    }

    @Override
    public void delete(String id) {
        RecordMetadata rmd = get(id); // needed for authorization check
        if(userAccessService.userHasAccessToRecord(rmd.getAcl())) {
            DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
            recordMetadataQueryHelper.deleteByPrimaryKey(RecordMetadataDoc.class, id);
            for (String legalTag : rmd.getLegal().getLegaltags()) {
                deleteLegalTagAssociation(id, legalTag);
            }
        }
    }

    @Override
    public RecordMetadata get(String id) {
        DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        RecordMetadataDoc doc = recordMetadataQueryHelper.loadByPrimaryKey(RecordMetadataDoc.class, id);
        if (doc == null) {
            return null;
        } else {
            return doc.getMetadata();
        }
    }

    @Override
    public Map<String, RecordMetadata> get(List<String> ids) {
        
        DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        
        Map<String, RecordMetadata> output = new HashMap<>();

        for (String id: ids) {            
            RecordMetadataDoc doc = recordMetadataQueryHelper.loadByPrimaryKey(RecordMetadataDoc.class, id);
            if (doc == null) continue;
            RecordMetadata rmd = doc.getMetadata();
            if (rmd == null) continue;
                output.put(id, rmd);
        }

        return output;
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit) {
        return null;
    }

    //TODO replace with the new method queryByLegal
    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
            String legalTagName, int limit, String cursor) {

        DynamoDBQueryHelperV2 legalTagQueryHelper = getLegalTagQueryHelper();
        
        LegalTagAssociationDoc legalTagAssociationDoc = new LegalTagAssociationDoc();
        legalTagAssociationDoc.setLegalTag(legalTagName);
        QueryPageResult<LegalTagAssociationDoc> result = null;
        try {
            result = legalTagQueryHelper.queryPage(LegalTagAssociationDoc.class,
                    legalTagAssociationDoc, 500, cursor);
        } catch (UnsupportedEncodingException e) {
            throw new AppException(org.apache.http.HttpStatus.SC_BAD_REQUEST, "Problem querying for legal tag", e.getMessage());
        }

        List<String> associatedRecordIds = new ArrayList<>();
        result.results.forEach(doc -> associatedRecordIds.add(doc.getRecordId())); // extract the Kinds from the SchemaDocs

        List<RecordMetadata> associatedRecords = new ArrayList<>();
        for(String recordId : associatedRecordIds){
            associatedRecords.add(get(recordId));
        }

        return new AbstractMap.SimpleEntry<>(result.cursor, associatedRecords);
    }

    private void saveLegalTagAssociation(String recordId, Set<String> legalTags){

        DynamoDBQueryHelperV2 legalTagQueryHelper = getLegalTagQueryHelper();

        for(String legalTag : legalTags){
            LegalTagAssociationDoc doc = new LegalTagAssociationDoc();
            doc.setLegalTag(legalTag);
            doc.setRecordId(recordId);
            doc.setRecordIdLegalTag(String.format("%s:%s", recordId, legalTag));
            legalTagQueryHelper.save(doc);
        }
    }

    private void deleteLegalTagAssociation(String recordId, String legalTag){

        DynamoDBQueryHelperV2 legalTagQueryHelper = getLegalTagQueryHelper();

        LegalTagAssociationDoc doc = new LegalTagAssociationDoc();
        doc.setRecordIdLegalTag(String.format("%s:%s", recordId, legalTag));
        legalTagQueryHelper.deleteByObject(doc);
    }
}
