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

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
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
@DynamoDbBean
public class SchemaDoc {

    private String kind;

    private String dataPartitionId;

    private String user;

    private List<SchemaItem> schemaItems;

    private Map<String,Object> extension;


    @DynamoDbPartitionKey
    @DynamoDbAttribute("Kind")
    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    // Sort key for GSI "SubjectLastPostedDateIndex" and sort key for LSI "ForumLastPostedDateIndex".
    @DynamoDbSecondaryPartitionKey(indexNames = {"DataPartitionId-User-Index"})
    @DynamoDbAttribute("DataPartitionId")
    public String getDataPartitionId() {
        return dataPartitionId;
    }

    public void setDataPartitionId(String dataPartitionId) {
        this.dataPartitionId = dataPartitionId;
    }

    @DynamoDbSecondarySortKey(indexNames = {"DataPartitionId-User-Index"})
    @DynamoDbSecondaryPartitionKey(indexNames = {"UserIndex"})
    @DynamoDbAttribute("User")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @DynamoDbConvertedBy(SchemaItemTypeConverter.class)
    @DynamoDbAttribute("schema")
    public List<SchemaItem> getSchemaItems() {
        return this.schemaItems;
    }

    public void setSchemaItems(List<SchemaItem> schemaItems) {
        this.schemaItems = schemaItems;
    }

    @DynamoDbConvertedBy(SchemaExtTypeConverter.class)
    @DynamoDbAttribute("ext")
    public Map<String,Object> getExtension() {
        return this.extension;
    }

    public void setExtension(Map<String,Object> extension) {
        this.extension = extension;
    }
}

