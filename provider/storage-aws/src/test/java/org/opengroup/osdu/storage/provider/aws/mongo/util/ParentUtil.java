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

package org.opengroup.osdu.storage.provider.aws.mongo.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.opengroup.osdu.core.aws.partition.PartitionInfoAws;
import org.opengroup.osdu.core.aws.partition.PartitionServiceClientWithCache;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.partition.Property;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.mockito.ArgumentMatchers.anyString;

public abstract class ParentUtil extends DbUtil {

    public MongoTemplateHelper mongoTemplateHelper;

    @MockBean
    private DpsHeaders headers;
    @MockBean
    private PartitionServiceClientWithCache partitionServiceClient;

    @BeforeEach
    void setUpMocks() {
        Mockito.when(this.headers.getPartitionId()).thenReturn(DATA_PARTITION);
        
        this.mongoTemplateHelper.dropCollections();
        
        PartitionInfoAws partitionInfoAws = new PartitionInfoAws();
        Property tenantIdProperty = new Property();
        tenantIdProperty.setValue(DATA_PARTITION);
        partitionInfoAws.setTenantIdProperty(tenantIdProperty);
        
        Mockito.when(partitionServiceClient.getPartition(anyString())).thenReturn(partitionInfoAws);
    }
    @AfterEach
    public void tearDown() {
        this.mongoTemplateHelper.dropCollections();
    }

    @Autowired
    public void set(MongoTemplate mongoTemplate) {
        this.mongoTemplateHelper = new MongoTemplateHelper(mongoTemplate);
    }
}
