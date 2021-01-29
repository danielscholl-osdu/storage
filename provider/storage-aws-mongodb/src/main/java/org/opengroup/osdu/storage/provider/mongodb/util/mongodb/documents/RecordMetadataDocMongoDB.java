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

package org.opengroup.osdu.storage.provider.mongodb.util.mongodb.documents;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.aws.mongodb.BasicMongoDBDoc;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.springframework.data.annotation.Id;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecordMetadataDocMongoDB implements BasicMongoDBDoc {

    @Id
    private String id;

    private String kind;

    private String status;

    private String user;

    private RecordMetadata metadata;

    private Set<String> legaltags;

    public RecordMetadataDocMongoDB(RecordMetadata recordMetadata) {
        id = recordMetadata.getId();
        metadata = recordMetadata;
        kind = recordMetadata.getKind();
        legaltags = recordMetadata.getLegal().getLegaltags();
        status = recordMetadata.getStatus().name();
        user = recordMetadata.getUser();
    }

    @Override
    public String getCursor() {
        return id;
    }
}
