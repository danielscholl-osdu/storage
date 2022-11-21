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
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.provider.aws.mongo.dto.RecordMetadataMongoDBDto;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.inject.Inject;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.opengroup.osdu.storage.provider.aws.mongo.MongoDbRecordsMetadataRepository.RECORD_METADATA_PREFIX;
import static org.opengroup.osdu.storage.provider.aws.mongo.mongodb.EntityFieldPaths.DATA_KIND;
import static org.opengroup.osdu.storage.provider.aws.mongo.mongodb.EntityFieldPaths.ID;

/**
 * The type Query repository.
 */
@ConditionalOnProperty(prefix = "repository", name = "implementation", havingValue = "mongodb")
@Repository
public class MongoDbQueryRepository implements IQueryRepository {

    /**
     * The Headers.
     */
    @Inject
    DpsHeaders headers;

    @Inject
    private MongoDBMultiClusterFactory mongoDBMultiClusterFactory;

    @Inject
    private IndexUpdater indexUpdater;

    private String getDataPartitionId() {
        return this.headers.getPartitionId();
    }


    /**
     * Gets all kinds.Not implemented yet
     *
     * @param limit  the limit
     * @param cursor the cursor
     * @return the all kinds
     */
    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {
        return null;
    }

    /**
     * Gets all record ids from kind.
     *
     * @param kind   the kind
     * @param limit  the limit
     * @param cursor the cursor
     * @return the all record ids from kind
     */
    @Override
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor, Optional<CollaborationContext> collaborationContext) {
        String dataPartitionId = getDataPartitionId();
        Query query = Query.query(Criteria.where(DATA_KIND).is(kind));

        QueryPageResult<RecordMetadataMongoDBDto> queryPage = mongoDBMultiClusterFactory
                .getHelper(dataPartitionId)
                .queryPage(query, ID, cursor, RecordMetadataMongoDBDto.class, limit, getCollection(dataPartitionId));

        DatastoreQueryResult dqr = new DatastoreQueryResult();
        dqr.setCursor(queryPage.getCursor());
        dqr.setResults(queryPage.getResults().stream().map(RecordMetadataMongoDBDto::getId).collect(Collectors.toList()));
        return dqr;
    }

    private String getCollection(String dataPartitionId) {
        indexUpdater.checkIndex(dataPartitionId);
        return RECORD_METADATA_PREFIX + dataPartitionId;
    }
}
