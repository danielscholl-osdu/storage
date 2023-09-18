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
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.SchemaDoc;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;


import java.util.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class SchemaRepositoryImplTest {

    @InjectMocks
    // Created inline instead of with autowired because mocks were overwritten
    // due to lazy loading
    private SchemaRepositoryImpl repo;

    @Mock
    private DynamoDBQueryHelperV2 queryHelper;

    @Mock
    private DynamoDBQueryHelperFactory queryHelperFactory;
    
    @Mock
    private DpsHeaders headers;

    @BeforeEach
    void setUp() {
        openMocks(this);

        Mockito.when(queryHelperFactory.getQueryHelperForPartition(Mockito.any(DpsHeaders.class), Mockito.any()))
        .thenReturn(queryHelper);
    }

    @Test
    void createSchema() throws NoSuchFieldException {
        // Arrange
        Schema schema = new Schema();
        schema.setKind("tenant:source:type:1.0.0");
        SchemaItem item = new SchemaItem();
        item.setKind("schemaKind");
        item.setPath("schemaPath");
        SchemaItem[] schemaItems = new SchemaItem[1];
        schemaItems[0] = item;
        schema.setSchema(schemaItems);
        String user = "test-user";
        String dataPartitionId = "test-data-partition-id";
        SchemaDoc expectedSd = new SchemaDoc();
        expectedSd.setKind(schema.getKind());
        expectedSd.setExtension(schema.getExt());
        expectedSd.setUser(user);
        expectedSd.setSchemaItems(Arrays.asList(schema.getSchema()));
        expectedSd.setDataPartitionId(dataPartitionId);

        Mockito.when(headers.getPartitionId()).thenReturn(dataPartitionId);
        Mockito.doNothing().when(queryHelper).save(Mockito.eq(expectedSd));

        // Act
        repo.add(schema, user);

        // Assert
        Mockito.verify(queryHelper, Mockito.times(1)).save(expectedSd);
    }

    @Test
    void addScehmaThrowsException() {
        when(queryHelper.keyExistsInTable(eq(SchemaDoc.class), any())).thenThrow(IllegalArgumentException.class);

        assertThrows(IllegalArgumentException.class, () -> repo.add(new Schema(), "user"));
    }
    @Test
    void getSchema() throws NoSuchFieldException {
        // Arrange
        String kind = "tenant:source:type:1.0.0";
        Schema expectedSchema = new Schema();
        expectedSchema.setKind("tenant:source:type:1.0.0");
        SchemaItem item = new SchemaItem();
        item.setKind("schemaKind");
        item.setPath("schemaPath");
        SchemaItem[] schemaItems = new SchemaItem[1];
        schemaItems[0] = item;
        expectedSchema.setSchema(schemaItems);
        String user = "test-user";
        SchemaDoc expectedSd = new SchemaDoc();
        expectedSd.setKind(expectedSchema.getKind());
        expectedSd.setExtension(expectedSchema.getExt());
        expectedSd.setUser(user);
        expectedSd.setSchemaItems(Arrays.asList(expectedSchema.getSchema()));
        Mockito.when(queryHelper.loadByPrimaryKey(Mockito.eq(SchemaDoc.class), Mockito.anyString()))
                .thenReturn(expectedSd);

        // Act
        Schema schema = repo.get(kind);

        // Assert
        Assert.assertEquals(schema, expectedSchema);
    }

    @Test
    void getSchemaReturnsNull() {
        String kind = "tenant:source:type:1.0.0";
        when(queryHelper.loadByPrimaryKey(SchemaDoc.class, kind)).thenReturn(null);
        Schema result = repo.get(kind);

        Assert.assertNull(result);
    }

    @Test
    void deleteSchema() throws NoSuchFieldException {
        // Arrange
        String kind = "tenant:source:type:1.0.0";
        SchemaDoc expectedSd = new SchemaDoc();
        expectedSd.setKind(kind);

        Mockito.doNothing().when(queryHelper).deleteByPrimaryKey(Mockito.eq(SchemaDoc.class), Mockito.anyString());

        // Act
        repo.delete(kind);

        // Assert
        Mockito.verify(queryHelper, Mockito.times(1)).deleteByPrimaryKey(Mockito.eq(SchemaDoc.class), Mockito.anyString());
    }
}
