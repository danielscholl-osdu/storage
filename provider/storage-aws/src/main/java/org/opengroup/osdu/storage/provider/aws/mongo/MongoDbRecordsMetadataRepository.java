// Copyright MongoDB, Inc or its affiliates. All Rights Reserved.
// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opengroup.osdu.storage.provider.aws.mongo;

import org.opengroup.osdu.core.aws.mongodb.MongoDBMultiClusterFactory;
import org.opengroup.osdu.core.aws.mongodb.entity.QueryPageResult;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.aws.mongo.dto.RecordMetadataMongoDBDto;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.inject.Inject;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.opengroup.osdu.storage.provider.aws.mongo.mongodb.EntityFieldPaths.DATA_LEGAL_LEGALTAGS;
import static org.opengroup.osdu.storage.provider.aws.mongo.mongodb.EntityFieldPaths.ID;

/**
 * The type Records metadata repository.
 */
@ConditionalOnProperty(prefix = "repository", name = "implementation", havingValue = "mongodb")
@Repository
public class MongoDbRecordsMetadataRepository implements IRecordsMetadataRepository<String> {

    public static final String RECORD_METADATA_PREFIX = "RecordMetadata-";

    @Inject
    private DpsHeaders headers;

    @Inject
    private MongoDBMultiClusterFactory mongoDBMultiClusterFactory;

    @Inject
    private IndexUpdater indexUpdater;

    private String getDataPartitionId() {
        return this.headers.getPartitionId();
    }

    /**
     * Create or update list.
     *
     * @param recordsMetadata the records metadata
     * @return the list
     */
    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata) {
        String dataPartitionId = getDataPartitionId();
        String collection = getCollection(dataPartitionId);
        recordsMetadata.stream()
                .map(RecordMetadataMongoDBDto::new)
                .forEach(record -> mongoDBMultiClusterFactory.getHelper(dataPartitionId).save(record, collection));
        return recordsMetadata;
    }

    /**
     * Delete.
     *
     * @param id the id
     */
    @Override
    public void delete(String id) {
        String dataPartitionId = getDataPartitionId();
        mongoDBMultiClusterFactory.getHelper(dataPartitionId).delete(ID, id, getCollection(dataPartitionId));
    }

    /**
     * Get record metadata.
     *
     * @param id the id
     * @return the record metadata
     */
    @Override
    public RecordMetadata get(String id) {
        String dataPartitionId = getDataPartitionId();
        RecordMetadataMongoDBDto recordMetadataMongoDBDto = mongoDBMultiClusterFactory
                .getHelper(dataPartitionId)
                .getById(id, RecordMetadataMongoDBDto.class, getCollection(dataPartitionId));
        return recordMetadataMongoDBDto == null ? null : recordMetadataMongoDBDto.getData();
    }

    /**
     * Get map.
     *
     * @param ids the ids
     * @return the map
     */
    @Override
    public Map<String, RecordMetadata> get(List<String> ids) {
        String dataPartitionId = getDataPartitionId();
        return mongoDBMultiClusterFactory
                .getHelper(dataPartitionId)
                .getList(ID, ids, RecordMetadataMongoDBDto.class, getCollection(dataPartitionId))
                .stream().collect(Collectors.toMap(RecordMetadataMongoDBDto::getId, RecordMetadataMongoDBDto::getData));
    }

    /**
     * Query by legal abstract map . simple entry.
     *
     * @param legalTagName the legal tag name
     * @param status       the status
     * @param limit        the limit
     * @return the abstract map . simple entry
     */
    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit) {
        return null;
    }

    /**
     * Query by legal tag name abstract map . simple entry.
     *
     * @param legalTagName the legal tag name
     * @param limit        the limit
     * @param cursor       the cursor
     * @return the abstract map . simple entry
     */
    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
            String legalTagName, int limit, String cursor) {
        String dataPartitionId = getDataPartitionId();
        Query query = Query.query(Criteria.where(DATA_LEGAL_LEGALTAGS).is(legalTagName));
        QueryPageResult<RecordMetadataMongoDBDto> queryPage = mongoDBMultiClusterFactory
                .getHelper(dataPartitionId)
                .queryPage(query, ID, cursor, RecordMetadataMongoDBDto.class, limit, getCollection(dataPartitionId));

        return new AbstractMap.SimpleEntry<>(queryPage.getCursor(),
                queryPage.getResults().stream()
                        .map(RecordMetadataMongoDBDto::getData)
                        .collect(Collectors.toList()));
    }

    private String getCollection(String dataPartitionId) {
        indexUpdater.checkIndex(dataPartitionId);
        return RECORD_METADATA_PREFIX + dataPartitionId;
    }
}
