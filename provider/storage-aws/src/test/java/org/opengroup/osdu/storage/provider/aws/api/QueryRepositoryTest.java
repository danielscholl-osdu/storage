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

package org.opengroup.osdu.storage.provider.aws.api;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.storage.provider.aws.QueryRepositoryImpl;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.SchemaDoc;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes = {StorageApplication.class})
public class QueryRepositoryTest {

    @InjectMocks
    // Created inline instead of with autowired because mocks were overwritten
    // due to lazy loading
    private QueryRepositoryImpl repo = new QueryRepositoryImpl();

    @Mock
    private DynamoDBQueryHelper queryHelper;

    @Mock
    private DpsHeaders dpsHeaders;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void getAllKinds() throws UnsupportedEncodingException {
        // Arrange
        String dataPartitionId = "test-data-partition-id";
        String kind = dataPartitionId + ":source:type:1.0.0";
        String cursor = "abc123";
        String user = "test-user@testing.com";

        List<String> resultsKinds = new ArrayList<>();
        resultsKinds.add(kind);
        DatastoreQueryResult expectedDatastoreQueryResult = new DatastoreQueryResult(cursor, resultsKinds);
        Schema expectedSchema = new Schema();
        expectedSchema.setKind(kind);

        SchemaItem item = new SchemaItem();
        item.setKind(kind);
        item.setPath("schemaPath");
        SchemaItem[] schemaItems = new SchemaItem[1];
        schemaItems[0] = item;
        expectedSchema.setSchema(schemaItems);

        SchemaDoc expectedSd = new SchemaDoc();
        expectedSd.setKind(expectedSchema.getKind());
        expectedSd.setExtension(expectedSchema.getExt());
        expectedSd.setUser(user);
        expectedSd.setSchemaItems(Arrays.asList(expectedSchema.getSchema()));

        List<SchemaDoc> expectedSchemaDocList = new ArrayList<>();
        expectedSchemaDocList.add(expectedSd);
        QueryPageResult<SchemaDoc> expectedQueryPageResult = new QueryPageResult<>(cursor, expectedSchemaDocList);

        Mockito.when(dpsHeaders.getPartitionId()).thenReturn(dataPartitionId);
        Mockito.when(queryHelper.queryByGSI(Mockito.eq(SchemaDoc.class),
                Mockito.anyObject(), Mockito.anyInt(), Mockito.eq(cursor)))
                .thenReturn(expectedQueryPageResult);

        // Act
        DatastoreQueryResult datastoreQueryResult = repo.getAllKinds(50, cursor);

        // Assert
        Assert.assertEquals(datastoreQueryResult, expectedDatastoreQueryResult);
    }

    @Test
    public void getAllRecordIdsFromKind() throws UnsupportedEncodingException {
        // Arrange
        String kind = "tenant:source:type:1.0.0";
        String cursor = "abc123";
        String recordId = "tenant:source:type:1.0.0.1212";
        List<String> resultsIds = new ArrayList<>();
        resultsIds.add(recordId);
        DatastoreQueryResult expectedDatastoreQueryResult = new DatastoreQueryResult(cursor, resultsIds);
        String user = "test-user";
        RecordMetadataDoc expectedRecordMetadataDoc = new RecordMetadataDoc();
        expectedRecordMetadataDoc.setId(recordId);
        expectedRecordMetadataDoc.setKind(kind);
        expectedRecordMetadataDoc.setUser(user);
        expectedRecordMetadataDoc.setStatus("active");
        List<RecordMetadataDoc> expectedRecordMetadataDocList = new ArrayList<>();
        expectedRecordMetadataDocList.add(expectedRecordMetadataDoc);
        QueryPageResult<RecordMetadataDoc> expectedQueryPageResult = new QueryPageResult<>(cursor, expectedRecordMetadataDocList);
        // Set GSI hash key
        RecordMetadataDoc recordMetadataKey = new RecordMetadataDoc();
        recordMetadataKey.setKind(kind);

        Mockito.when(queryHelper.queryPage(Mockito.eq(RecordMetadataDoc.class), Mockito.anyObject(), Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.eq(cursor)))
                .thenReturn(expectedQueryPageResult);

        // Act
        DatastoreQueryResult datastoreQueryResult = repo.getAllRecordIdsFromKind(kind, 50, cursor);

        // Assert
        Mockito.verify(queryHelper, Mockito.times(1)).queryPage(Mockito.eq(RecordMetadataDoc.class), Mockito.anyObject(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyInt(), Mockito.eq(cursor));
        Assert.assertEquals(expectedDatastoreQueryResult, datastoreQueryResult);
    }
}
