// Copyright © Amazon Web Services
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

package org.opengroup.osdu.storage.provider.mongodb;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.aws.mongodb.MongoDBHelper;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.provider.mongodb.util.mongodb.documents.LegalTagAssociationDocMongoDB;
import org.opengroup.osdu.storage.provider.mongodb.util.mongodb.documents.RecordMetadataDocMongoDB;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Repository
public class RecordsMetadataRepositoryImplMongoDB implements IRecordsMetadataRepository<String> {

    private final MongoDBHelper queryHelper;

    @PostConstruct
    public void init() {
        queryHelper.ensureIndex(LegalTagAssociationDocMongoDB.class, new Index().on("legalTag", Sort.Direction.ASC));
        //TODO: research performance difference about moving RecordMetadataDocMongoDB indexes out from top level of document
        queryHelper.ensureIndex(RecordMetadataDocMongoDB.class, new Index().on("kind", Sort.Direction.ASC));
        queryHelper.ensureIndex(RecordMetadataDocMongoDB.class, new Index().on("status", Sort.Direction.ASC));
        queryHelper.ensureIndex(RecordMetadataDocMongoDB.class, new Index().on("user", Sort.Direction.ASC));
    }

    /**
     * Saves or updates entities in DB
     */
    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata) {
        if (recordsMetadata == null) return null;
        createLegalTagAssociations(recordsMetadata);

        val recordDocs = recordsMetadata.stream()
                .map(RecordMetadataDocMongoDB::new)
                .collect(Collectors.toList());

        // TODO Performance optimizations here
        recordDocs.forEach(queryHelper::save);

        return recordsMetadata;
    }

    @Override
    public void delete(String id) {
        RecordMetadata rmd = get(id); // needed for authorization check
        queryHelper.deleteByPrimaryKey(RecordMetadataDocMongoDB.class, id);
        for (String legalTag : rmd.getLegal().getLegaltags()) {
            deleteLegalTagAssociation(id, legalTag);
        }
    }

    private List<RecordMetadataDocMongoDB> getByIds(Set<String> ids) {
        Query legalTagQuery = new Query(Criteria.where("id").in(ids));
        return queryHelper.getByQuery(legalTagQuery, RecordMetadataDocMongoDB.class);
    }

    @Override
    public RecordMetadata get(String id) {
        RecordMetadataDocMongoDB doc = queryHelper.getById(id, RecordMetadataDocMongoDB.class);
        if (doc == null) {
            return null;
        } else {
            return doc.getMetadata();
        }
    }

    @Override
    public Map<String, RecordMetadata> get(List<String> ids) {
        return queryHelper.multiGetByField(ids, "id", RecordMetadataDocMongoDB.class)
                .stream()
                .collect(Collectors.toMap(RecordMetadataDocMongoDB::getId, RecordMetadataDocMongoDB::getMetadata));
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
            String legalTagName, int limit, String cursor) {

        Query legalTagQuery = new Query(Criteria.where("legalTag").is(legalTagName));
        QueryPageResult<LegalTagAssociationDocMongoDB> result = queryHelper.queryPage(LegalTagAssociationDocMongoDB.class,
                legalTagQuery, "recordIdLegalTag", cursor, limit);

        Set<String> associatedRecordIds = new HashSet<>();
        result.results.forEach(doc -> associatedRecordIds.add(doc.getRecordId())); // extract the Kinds from the SchemaDocs

        List<RecordMetadataDocMongoDB> docList = getByIds(associatedRecordIds);
        List<RecordMetadata> associatedRecords = docList.stream().map(RecordMetadataDocMongoDB::getMetadata).collect(Collectors.toList());

        return new AbstractMap.SimpleEntry<>(result.cursor, associatedRecords);
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit) {
        return null;
    }

    private void saveLegalTagAssociation(String recordId, Set<String> legalTags) {
        for (String legalTag : legalTags) {
            LegalTagAssociationDocMongoDB doc = new LegalTagAssociationDocMongoDB();
            doc.setLegalTag(legalTag);
            doc.setRecordId(recordId);
            doc.setRecordIdLegalTag(String.format("%s:%s", recordId, legalTag));
            // TODO Do this async
            queryHelper.save(doc);
        }
    }

    private void createLegalTagAssociations(List<RecordMetadata> recordMetadataList) {
        val list = new LinkedList<LegalTagAssociationDocMongoDB>();
        for (val record : recordMetadataList) {
            for (String legalTag : record.getLegal().getLegaltags()) {
                LegalTagAssociationDocMongoDB doc = new LegalTagAssociationDocMongoDB();
                doc.setLegalTag(legalTag);
                doc.setRecordId(record.getId());
                doc.setRecordIdLegalTag(String.format("%s:%s", record.getId(), legalTag));
                queryHelper.save(doc); // TODO remove this sometimes
            }
        }
    }

    private void saveMultipleDocs(List<RecordMetadataDocMongoDB> recordDocs) {
        // TODO Do some performance optimizations here
        recordDocs.forEach(queryHelper::save);
    }

    private void deleteLegalTagAssociation(String recordId, String legalTag) {
        queryHelper.deleteByPrimaryKey(LegalTagAssociationDocMongoDB.class, String.format("%s:%s", recordId, legalTag));
    }
}
