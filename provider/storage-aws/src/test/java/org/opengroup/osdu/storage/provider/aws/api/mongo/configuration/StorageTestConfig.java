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

package org.opengroup.osdu.storage.provider.aws.api.mongo.configuration;

import org.mockito.Mockito;
import org.opengroup.osdu.core.aws.mongodb.MongoDBSimpleFactory;
import org.opengroup.osdu.core.aws.mongodb.MultiClusteredConfigReader;
import org.opengroup.osdu.core.aws.mongodb.config.MongoProperties;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.storage.conversion.CrsConversionService;
import org.opengroup.osdu.storage.provider.aws.CloudStorageImpl;
import org.opengroup.osdu.storage.provider.aws.MessageBusImpl;
import org.opengroup.osdu.storage.provider.aws.SchemaRepositoryImpl;
import org.opengroup.osdu.storage.provider.aws.SomeBasicInterfaceImpl;
import org.opengroup.osdu.storage.provider.aws.cache.GroupCache;
import org.opengroup.osdu.storage.provider.aws.cache.LegalTagCache;
import org.opengroup.osdu.storage.provider.aws.mongo.mongodb.MultiClusteredConfigReaderStorage;
import org.opengroup.osdu.storage.provider.aws.security.UserAccessService;
import org.opengroup.osdu.storage.provider.aws.service.BatchServiceAWSImpl;
import org.opengroup.osdu.storage.service.BulkUpdateRecordServiceImpl;
import org.opengroup.osdu.storage.service.DataAuthorizationService;
import org.opengroup.osdu.storage.service.IngestionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.mockito.ArgumentMatchers.any;


@TestConfiguration
public class StorageTestConfig {
    private final MongoProperties properties = MongoProperties.builder().
            endpoint("localhost:27019").
            databaseName("test")
            .build();

    @Bean
    @Primary
    public MultiClusteredConfigReader configReaderStorage() {
        MultiClusteredConfigReaderStorage multiClusteredConfigReaderStorage = Mockito.mock(MultiClusteredConfigReaderStorage.class);
        Mockito.doReturn(properties).when(multiClusteredConfigReaderStorage).readProperties(any());
        return multiClusteredConfigReaderStorage;
    }

    @MockBean
    public CloudStorageImpl cloudStorage;
    @MockBean
    public MessageBusImpl messageBus;
    @MockBean
    public SchemaRepositoryImpl schemaRepository;
    @MockBean
    public SomeBasicInterfaceImpl someBasicInterface;
    @MockBean
    public GroupCache groupCache;
    @MockBean
    public LegalTagCache legalTagCache;
    @MockBean
    public UserAccessService userAccessService;
    @MockBean
    public BatchServiceAWSImpl batchServiceAWS;
    @MockBean
    public CrsConversionService crsConversionService;
    @MockBean
    public BulkUpdateRecordServiceImpl bulkUpdateRecordService;
    @MockBean
    public DataAuthorizationService dataAuthorizationService;
    @MockBean
    public IngestionService ingestionService;
    @MockBean
    public IServiceAccountJwtClient serviceAccountJwtClient;

    @Bean
    public MongoTemplate createMongoTemplate(MongoDBSimpleFactory dbSimpleFactory) {
        return dbSimpleFactory.mongoTemplate(properties);
    }
}
