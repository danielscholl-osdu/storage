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

package org.opengroup.osdu.storage.provider.aws.util.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.converters.SchemaExtTypeConverter;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.converters.SchemaItemTypeConverter;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@DynamoDBTable(tableName = "SchemaRepository") // DynamoDB table name (without environment prefix)
public class SchemaDoc {

    @DynamoDBHashKey(attributeName = "Kind")
    private String kind;

    @DynamoDBIndexHashKey(globalSecondaryIndexName = "DataPartitionId-User-Index")
    @DynamoDBAttribute(attributeName = "DataPartitionId")
    private String dataPartitionId;

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "DataPartitionId-User-Index")
    @DynamoDBAttribute(attributeName = "User")
    private String user;

    @DynamoDBTypeConverted(converter = SchemaItemTypeConverter.class)
    @DynamoDBAttribute(attributeName = "schema")
    private List<SchemaItem> schemaItems;

    @DynamoDBTypeConverted(converter = SchemaExtTypeConverter.class)
    @DynamoDBAttribute(attributeName = "ext")
    private Map<String,Object> extension;
}

