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
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.converters.LegalTagsTypeConverter;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.converters.RecordMetadataTypeConverter;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@DynamoDbBean
public class RecordMetadataDoc {

    private String id;

    private String kind;

    private String status;

    private String user;

    private RecordMetadata metadata;

    private Set<String> legaltags;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("Id")
    public String getId() {
        return id;
    }

    public void setKId(String id) {
        this.id = id;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"KindStatusIndex"})
    @DynamoDbAttribute("Kind")
    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    @DynamoDbSecondarySortKey(indexNames = {"KindStatusIndex"})
    @DynamoDbAttribute("Status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"UserIndex"})
    @DynamoDbAttribute("User")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @DynamoDbConvertedBy(RecordMetadataTypeConverter.class)
    @DynamoDbAttribute("metadata")
    public RecordMetadata getMetadata() {
        return this.metadata;
    }

    public void setMetadata(RecordMetadata metadata) {
        this.metadata = metadata;
    }

    @DynamoDbConvertedBy(LegalTagsTypeConverter.class)
    @DynamoDbAttribute("LegalTags")
    public Set<String> getLegaltags() {
        return this.legaltags;
    }

    public void setLegaltags(Set<String> legaltags) {
        this.legaltags = legaltags;
    }
}
