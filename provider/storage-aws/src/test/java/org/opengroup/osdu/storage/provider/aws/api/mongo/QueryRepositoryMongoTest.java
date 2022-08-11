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

package org.opengroup.osdu.storage.provider.aws.api.mongo;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.provider.aws.api.mongo.configuration.StorageTestConfig;
import org.opengroup.osdu.storage.provider.aws.api.mongo.util.DbUtil;
import org.opengroup.osdu.storage.provider.aws.api.mongo.util.ParentUtil;
import org.opengroup.osdu.storage.provider.aws.api.mongo.util.RecordMetadataGenerator;
import org.opengroup.osdu.storage.provider.aws.mongo.MongoDbQueryRepository;
import org.opengroup.osdu.storage.provider.aws.mongo.dto.RecordMetadataMongoDBDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@DataMongoTest
@RunWith(SpringRunner.class)
@SpringJUnitConfig(classes = StorageTestConfig.class)
public class QueryRepositoryMongoTest extends ParentUtil {

    @Autowired
    private MongoDbQueryRepository queryRepository;

    @Test
    public void getAllRecordIdsFromKind() {
        //given
        String someKind = "someKind";
        List<RecordMetadataMongoDBDto> firstList = RecordMetadataGenerator.generate(27, someKind);
        String otherKind = "otherKind";
        List<RecordMetadataMongoDBDto> secondList = RecordMetadataGenerator.generate(15, otherKind);
        mongoTemplateHelper.insert(firstList, DbUtil.DATA_PARTITION);
        mongoTemplateHelper.insert(secondList, DbUtil.DATA_PARTITION);
        List<RecordMetadataMongoDBDto> all = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(firstList.size() + secondList.size(), all.size());

        //when
        DatastoreQueryResult firstPageRecords = queryRepository.getAllRecordIdsFromKind(someKind, 5, null);
        String cursor1 = firstPageRecords.getCursor();
        List<String> firstPageRecordsIds = firstPageRecords.getResults();

        DatastoreQueryResult secondPageRecords = queryRepository.getAllRecordIdsFromKind(someKind, 20, cursor1);
        String cursor2 = secondPageRecords.getCursor();
        List<String> secondPageRecordsIds = secondPageRecords.getResults();

        DatastoreQueryResult lastPageRecords = queryRepository.getAllRecordIdsFromKind(someKind, 5, cursor2);
        String cursor3 = lastPageRecords.getCursor();
        List<String> lastPageRecordsIds = lastPageRecords.getResults();


        DatastoreQueryResult otherRecords = queryRepository.getAllRecordIdsFromKind(otherKind, 50, null);
        String cursor4 = otherRecords.getCursor();
        List<String> otherResults = otherRecords.getResults();


        //then
        assertNotNull(cursor1);
        assertEquals(5, firstPageRecordsIds.size());

        assertNotNull(cursor2);
        assertEquals(20, secondPageRecordsIds.size());

        assertNull(cursor3);
        assertEquals(2, lastPageRecordsIds.size());

        assertNull(cursor4);
        assertEquals(15, otherResults.size());
    }
}
