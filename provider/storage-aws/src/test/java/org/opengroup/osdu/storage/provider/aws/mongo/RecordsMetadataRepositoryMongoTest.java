
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

package org.opengroup.osdu.storage.provider.aws.mongo;


import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.provider.aws.mongo.configuration.StorageTestConfig;
import org.opengroup.osdu.storage.provider.aws.mongo.util.DbUtil;
import org.opengroup.osdu.storage.provider.aws.mongo.util.ParentUtil;
import org.opengroup.osdu.storage.provider.aws.mongo.util.RecordMetadataGenerator;
import org.opengroup.osdu.storage.provider.aws.mongo.dto.RecordMetadataMongoDBDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@DataMongoTest
@SpringJUnitConfig(classes = StorageTestConfig.class)
class RecordsMetadataRepositoryMongoTest extends ParentUtil {

    @Autowired
    private MongoDbRecordsMetadataRepository recordsMetadataRepository;

    @Test
    void createRecordMetadata() {
        //given
        List<RecordMetadataMongoDBDto> allFromHelper = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(0, allFromHelper.size());

        List<RecordMetadata> recordMetadataList = RecordMetadataGenerator.generate(5, "legalTag").stream().map(RecordMetadataMongoDBDto::getData).collect(Collectors.toList());

        //when
        List<RecordMetadata> recordMetadataListFromRepo = recordsMetadataRepository.createOrUpdate(recordMetadataList, Optional.empty());

        //then
        allFromHelper = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(recordMetadataList.size(), allFromHelper.size());
        List<String> idsFromRepo = recordMetadataListFromRepo.stream().map(RecordMetadata::getId).collect(Collectors.toList());
        List<String> idsFromDb = allFromHelper.stream().map(RecordMetadataMongoDBDto::getId).collect(Collectors.toList());
        assertTrue(idsFromDb.containsAll(idsFromRepo));
    }

    @Test
    void createAndUpdateRecordMetadata() {
        //given
        List<RecordMetadataMongoDBDto> existsList = RecordMetadataGenerator.generate(5, "legalTag");
        mongoTemplateHelper.insert(existsList, DbUtil.DATA_PARTITION);
        List<RecordMetadataMongoDBDto> allFromHelper = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(existsList.size(), allFromHelper.size());
        assertEquals(RecordState.active, allFromHelper.stream().findAny().get().getData().getStatus());

        List<RecordMetadata> existsListAfterChange = existsList.stream().map(recordMetadataMongoDto -> {
            RecordMetadata data = recordMetadataMongoDto.getData();
            data.setStatus(RecordState.deleted);
            return data;
        }).collect(Collectors.toList());


        int notExistrecordsCount = 8;
        List<RecordMetadata> notExistsList = RecordMetadataGenerator.generate(notExistrecordsCount, "legalTag").stream().map(RecordMetadataMongoDBDto::getData).collect(Collectors.toList());

        notExistsList.addAll(existsListAfterChange);
        assertEquals(existsListAfterChange.size() + notExistrecordsCount, notExistsList.size());

        //when
        List<RecordMetadata> recordMetadataListFromRepo = recordsMetadataRepository.createOrUpdate(notExistsList, Optional.empty());

        //then
        allFromHelper = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(notExistsList.size(), allFromHelper.size());
        List<String> idsFromRepo = recordMetadataListFromRepo.stream().map(RecordMetadata::getId).collect(Collectors.toList());
        List<String> idsFromDb = allFromHelper.stream().map(RecordMetadataMongoDBDto::getId).collect(Collectors.toList());
        assertTrue(idsFromDb.containsAll(idsFromRepo));
        assertEquals(RecordState.deleted, allFromHelper.stream().filter(recordMetadataMongoDto -> existsListAfterChange.stream().map(RecordMetadata::getId).collect(Collectors.toList()).contains(recordMetadataMongoDto.getId())).findAny().get().getData().getStatus());
    }

    @Test
    void delete() {
        // given
        RecordMetadataMongoDBDto recordMetadataMongoDBDto = RecordMetadataGenerator.create();
        String id = recordMetadataMongoDBDto.getData().getId();

        assertNotNull(mongoTemplateHelper.insert(recordMetadataMongoDBDto));
        List<RecordMetadataMongoDBDto> recordMetadataMongoDBDtos = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(1, recordMetadataMongoDBDtos.size());

        //when
        recordsMetadataRepository.delete(id, Optional.empty());

        //then
        recordMetadataMongoDBDtos = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(0, recordMetadataMongoDBDtos.size());
    }

    @Test
    void deleteIfNotExists() {
        // given
        List<RecordMetadataMongoDBDto> recordMetadataMongoDBDtos = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(0, recordMetadataMongoDBDtos.size());

        //when
        recordsMetadataRepository.delete("anyId", Optional.empty());

        //then
        recordMetadataMongoDBDtos = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(0, recordMetadataMongoDBDtos.size());
    }

    @Test
    void get() {
        // given
        RecordMetadataMongoDBDto recordMetadataMongoDBDto = RecordMetadataGenerator.create();
        String id = recordMetadataMongoDBDto.getData().getId();

        assertNotNull(mongoTemplateHelper.insert(recordMetadataMongoDBDto));

        //when
        RecordMetadata recordMetadataFromRepo = recordsMetadataRepository.get(id, Optional.empty());

        //then
        assertEquals(id, recordMetadataFromRepo.getId());
    }

    @Test
    void getNotFound() {
        // given
        List<RecordMetadataMongoDBDto> all = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(0, all.size());

        //when
        RecordMetadata recordMetadataFromRepo = recordsMetadataRepository.get(RECORD_ID, Optional.empty());

        //then
        assertNull(recordMetadataFromRepo);
    }

    @Test
    void getList() {
        //given
        List<RecordMetadataMongoDBDto> firstList = RecordMetadataGenerator.generate(5, "legalTag");
        List<RecordMetadataMongoDBDto> secondList = RecordMetadataGenerator.generate(5, "otherLegalTag");
        mongoTemplateHelper.insert(firstList, DbUtil.DATA_PARTITION);
        mongoTemplateHelper.insert(secondList, DbUtil.DATA_PARTITION);
        List<RecordMetadataMongoDBDto> all = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(firstList.size() + secondList.size(), all.size());

        //when
        Map<String, RecordMetadata> stringRecordMetadataMap = recordsMetadataRepository.get(firstList.stream().map(RecordMetadataMongoDBDto::getId).collect(Collectors.toList()), Optional.empty());

        //then
        stringRecordMetadataMap.forEach((key, value) -> {
            assertEquals(key, value.getId());
            assertTrue(firstList.stream().map(RecordMetadataMongoDBDto::getId).anyMatch(id -> id.equals(key)));
            assertTrue(secondList.stream().map(RecordMetadataMongoDBDto::getId).noneMatch(id -> id.equals(key)));
        });
    }

    @Test
    void queryByLegalTagName() {
        //given
        String legalTagName = "legalTag";
        List<RecordMetadataMongoDBDto> firstList = RecordMetadataGenerator.generate(27, legalTagName);
        String otherLegalTag = "otherLegalTag";
        List<RecordMetadataMongoDBDto> secondList = RecordMetadataGenerator.generate(15, otherLegalTag);
        mongoTemplateHelper.insert(firstList, DbUtil.DATA_PARTITION);
        mongoTemplateHelper.insert(secondList, DbUtil.DATA_PARTITION);
        List<RecordMetadataMongoDBDto> all = mongoTemplateHelper.findAll(DbUtil.DATA_PARTITION);
        assertEquals(firstList.size() + secondList.size(), all.size());

        //when
        legalTagName = legalTagName + "0";
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> firstPage = recordsMetadataRepository.queryByLegalTagName(legalTagName, 5, null);
        String cursor1 = firstPage.getKey();

        List<RecordMetadata> firstPageRecords = firstPage.getValue();
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> secondPage = recordsMetadataRepository.queryByLegalTagName(legalTagName, 20, cursor1);
        String cursor2 = secondPage.getKey();

        List<RecordMetadata> secondPageRecords = secondPage.getValue();
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> lastPage = recordsMetadataRepository.queryByLegalTagName(legalTagName, 5, cursor2);
        String cursor3 = lastPage.getKey();
        List<RecordMetadata> lastPageRecords = lastPage.getValue();

        otherLegalTag = otherLegalTag + "1";
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> otherLegalTags = recordsMetadataRepository.queryByLegalTagName(otherLegalTag, 50, null);
        String cursor4 = otherLegalTags.getKey();
        List<RecordMetadata> otherLegalTagsValue = otherLegalTags.getValue();

        //then
        assertNotNull(cursor1);
        assertEquals(5, firstPageRecords.size());

        assertNotNull(cursor2);
        assertEquals(20, secondPageRecords.size());

        assertNull(cursor3);
        assertEquals(2, lastPageRecords.size());

        assertNull(cursor4);
        assertEquals(15, otherLegalTagsValue.size());
    }
}
