/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.web.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.service.Context;

@ExtendWith(MockitoExtension.class)
class OsmRecordsMetadataRepositoryTest {


  public static final String PARTITION_ID = "osdu";
  public static final String ID_1 = "id1";
  public static final String ID_2 = "id2";
  public static final String ID_3 = "id3";
  @Mock
  Context context;

  TenantInfo tenantInfo;

  OsmRecordsMetadataRepository recordsMetadataRepository;

  @Test
  void verifyCorrectArgsIfMoreThanOneRecordToDelete() {
    context = new Context(null, null) {
      @Override
      public <OET> void deleteById(Class<OET> entityType, Destination destination, String id, String... ids) {
        assertEquals(ID_1, id);
        List<String> strings = Arrays.stream(ids).toList();
        assertEquals(strings, List.of(ID_2));
      }
    };
    tenantInfo = new TenantInfo();
    tenantInfo.setName(PARTITION_ID);
    tenantInfo.setDataPartitionId(PARTITION_ID);
    recordsMetadataRepository = new OsmRecordsMetadataRepository(context, tenantInfo);

    List<String> immutableIds = List.of(ID_1, ID_2);
    recordsMetadataRepository.batchDelete(immutableIds, Optional.empty());
  }

  @Test
  void verifyCorrectArgsIfOneRecordToDelete() {
    context = new Context(null, null) {
      @Override
      public <OET> void deleteById(Class<OET> entityType, Destination destination, String id, String... ids) {
        assertEquals(ID_1, id);
        List<String> strings = Arrays.stream(ids).toList();
        assertTrue(strings.isEmpty());
      }
    };
    tenantInfo = new TenantInfo();
    tenantInfo.setName(PARTITION_ID);
    tenantInfo.setDataPartitionId(PARTITION_ID);
    recordsMetadataRepository = new OsmRecordsMetadataRepository(context, tenantInfo);

    List<String> immutableIds = List.of(ID_1);
    recordsMetadataRepository.batchDelete(immutableIds, Optional.empty());
  }
}