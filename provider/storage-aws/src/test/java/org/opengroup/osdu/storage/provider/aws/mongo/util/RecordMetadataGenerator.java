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

import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.provider.aws.mongo.dto.RecordMetadataMongoDBDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RecordMetadataGenerator extends DbUtil {
    public static RecordMetadataMongoDBDto create() {
        return create(RECORD_ID, "default");
    }

    public static RecordMetadataMongoDBDto create(String id, String someParameter) {
        RecordMetadata recordMetadata = new RecordMetadata();
        Legal legal = new Legal();
        Set<String> legalTags = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            legalTags.add(someParameter + i);
        }
        legal.setLegaltags(legalTags);
        recordMetadata.setLegal(legal);
        recordMetadata.setKind(someParameter);
        recordMetadata.setStatus(RecordState.active);
        recordMetadata.setId(id);
        return new RecordMetadataMongoDBDto(recordMetadata);
    }

    public static List<RecordMetadataMongoDBDto> generate(int count, String someParameter) {
        List<RecordMetadataMongoDBDto> recordMetadataMongoDBDtos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            recordMetadataMongoDBDtos.add(create(UUID.randomUUID().toString(), someParameter));
        }
        return recordMetadataMongoDBDtos;
    }
}
