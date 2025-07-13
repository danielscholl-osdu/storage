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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@DynamoDbBean
public class LegalTagAssociationDoc {

    private String recordIdLegalTag;

    private String recordId;

    private String legalTag;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("recordIdLegalTag")
    public String getRecordIdLegalTag() {
        return recordIdLegalTag;
    }

    public void setRecordIdLegalTag(String recordIdLegalTag) {
        this.recordIdLegalTag = recordIdLegalTag;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"recordId-index"})
    @DynamoDbAttribute("recordId")
    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"legalTag-index"})
    @DynamoDbAttribute("legalTag")
    public String getLegalTag() {
        return legalTag;
    }

    public void setLegalTag(String legalTag) {
        this.legalTag = legalTag;
    }

    public static String getLegalRecordId(String recordId, String legalTag) {
        return String.format("%s:%s", recordId, legalTag);
    }

    public static LegalTagAssociationDoc createLegalTagDoc(String legalTag, String recordId) {
        LegalTagAssociationDoc doc = new LegalTagAssociationDoc();
        doc.setLegalTag(legalTag);
        doc.setRecordId(recordId);
        doc.setRecordIdLegalTag(getLegalRecordId(recordId, legalTag));
        return doc;
    }
}
