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

import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.SchemaDoc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// To be deprecated
@Repository
public class SchemaRepositoryImpl implements ISchemaRepository {

    
    @Inject
    private DpsHeaders headers;

    @Inject
    private DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Value("${aws.dynamodb.schemaRepositoryTable.ssm.relativePath}")
    String schemaRepositoryTableParameterRelativePath;    


    private DynamoDBQueryHelper<SchemaDoc> getSchemaTableQueryHelper() {
        return dynamoDBQueryHelperFactory.createQueryHelper(headers, schemaRepositoryTableParameterRelativePath, SchemaDoc.class);
    }

    @Override
    public void add(Schema schema, String user) {

        DynamoDBQueryHelper<SchemaDoc> schemaTableQueryHelper = getSchemaTableQueryHelper();

        SchemaDoc sd = new SchemaDoc();
        sd.setKind(schema.getKind());

        // Check if a schema with this kind already exists
        if (schemaTableQueryHelper.getItem(sd.getKind()).isPresent()) {
            throw new IllegalArgumentException("Schema " + sd.getKind() + " already exists. Can't create again.");
        }

        // Complete the SchemaDoc object and add it to the database
        sd.setExtension(schema.getExt());
        sd.setUser(user);
        sd.setSchemaItems(Arrays.asList(schema.getSchema()));
        sd.setDataPartitionId(headers.getPartitionId());
        schemaTableQueryHelper.putItem(sd);
    }

    @Override
    public Schema get(String kind) {
        
        DynamoDBQueryHelper<SchemaDoc> schemaTableQueryHelper = getSchemaTableQueryHelper();
        Optional<SchemaDoc> sdOptional = schemaTableQueryHelper.getItem(kind);
        if (sdOptional.isEmpty()) {
            return null;
        }

        // Create a Schema object and assign the values retrieved from DynamoDB
        Schema newSchema = new Schema();
        newSchema.setKind(kind);
        List<SchemaItem> schemaItemList = sdOptional.get().getSchemaItems();
        SchemaItem[] schemaItemsArray = schemaItemList.toArray(new SchemaItem[0]); // convert List to Array
        newSchema.setSchema(schemaItemsArray);
        newSchema.setExt(sdOptional.get().getExtension());
        return newSchema;
    }

    @Override
    public void delete(String kind) {
        DynamoDBQueryHelper<SchemaDoc> schemaTableQueryHelper = getSchemaTableQueryHelper();

        schemaTableQueryHelper.deleteItem(kind);
    }
}
