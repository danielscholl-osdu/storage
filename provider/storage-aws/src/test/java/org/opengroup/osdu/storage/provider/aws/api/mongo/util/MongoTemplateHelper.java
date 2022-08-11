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

package org.opengroup.osdu.storage.provider.aws.api.mongo.util;

import org.opengroup.osdu.storage.provider.aws.mongo.dto.RecordMetadataMongoDBDto;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Collection;
import java.util.List;

import static org.opengroup.osdu.storage.provider.aws.mongo.MongoDbRecordsMetadataRepository.RECORD_METADATA_PREFIX;


public final class MongoTemplateHelper extends DbUtil {
    private final MongoTemplate mongoTemplate;

    public MongoTemplateHelper(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public RecordMetadataMongoDBDto insert(RecordMetadataMongoDBDto recordMetadata) {
        return this.insert(recordMetadata, DbUtil.DATA_PARTITION);
    }

    public void insert(Collection<? extends RecordMetadataMongoDBDto> recordMetadataMongoDtos, String collection) {
        recordMetadataMongoDtos.forEach(t -> this.insert(t, collection));
    }

    public RecordMetadataMongoDBDto insert(RecordMetadataMongoDBDto recordMetadata, String dataPartition) {
        return this.mongoTemplate.insert(recordMetadata, RECORD_METADATA_PREFIX + dataPartition);
    }

    public List<RecordMetadataMongoDBDto> findAll(String dataPartition) {
        return this.mongoTemplate.findAll(RecordMetadataMongoDBDto.class, RECORD_METADATA_PREFIX + dataPartition);
    }

    public void dropCollections() {
        this.mongoTemplate.getCollectionNames().forEach(this.mongoTemplate::dropCollection);
    }
}
