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

import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.SchemaDoc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

@Repository
public class SchemaRepositoryImpl implements ISchemaRepository {

    @Value("${aws.dynamodb.table.prefix}")
    String tablePrefix;

    @Value("${aws.region}")
    String dynamoDbRegion;

    @Value("${aws.dynamodb.endpoint}")
    String dynamoDbEndpoint;

    @Inject
    private DpsHeaders headers;

    private DynamoDBQueryHelper queryHelper;

    @PostConstruct
    public void init(){
        queryHelper = new DynamoDBQueryHelper(dynamoDbEndpoint, dynamoDbRegion, tablePrefix);
    }

    @Override
    public void add(Schema schema, String user) {
        SchemaDoc sd = new SchemaDoc();
        sd.setKind(schema.getKind());

        // Check if a schema with this kind already exists
        if (queryHelper.keyExistsInTable(SchemaDoc.class, sd)) {
            throw new IllegalArgumentException("Schema " + sd.getKind() + " already exists. Can't create again.");
        }

        // Complete the SchemaDoc object and add it to the database
        sd.setExtension(schema.getExt());
        sd.setUser(user);
        sd.setSchemaItems(Arrays.asList(schema.getSchema()));
        sd.setDataPartitionId(headers.getPartitionId());
        queryHelper.save(sd);
    }

    @Override
    public Schema get(String kind) {
        SchemaDoc sd = queryHelper.loadByPrimaryKey(SchemaDoc.class, kind);
        if (sd == null) {
            return null;
        }

        // Create a Schema object and assign the values retrieved from DynamoDB
        Schema newSchema = new Schema();
        newSchema.setKind(kind);
        List<SchemaItem> schemaItemList = sd.getSchemaItems();
        SchemaItem[] schemaItemsArray = schemaItemList.toArray(new SchemaItem[0]); // convert List to Array
        newSchema.setSchema(schemaItemsArray);
        newSchema.setExt(sd.getExtension());
        return newSchema;
    }

    @Override
    public void delete(String kind) {
        queryHelper.deleteByPrimaryKey(SchemaDoc.class, kind);
    }
}
