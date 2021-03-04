// Copyright 2017-2021, Schlumberger
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

package org.opengroup.osdu.storage.service;

import static java.util.stream.Collectors.toList;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.core.common.storage.IPersistenceService;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.response.BulkUpdateRecordsResponse;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.opengroup.osdu.storage.validation.api.PatchOperationValidator;
import org.springframework.stereotype.Service;

@Service
public class BulkUpdateRecordServiceImpl implements BulkUpdateRecordService {

  private final IRecordsMetadataRepository recordRepository;
  private final PatchOperationValidator patchOperationValidator;
  private final IEntitlementsAndCacheService entitlementsAndCacheService;
  private final StorageAuditLogger auditLogger;
  private final IPersistenceService persistenceService;
  private final DpsHeaders headers;
  private final RecordUtil recordUtil;
  private final Clock clock;

  public BulkUpdateRecordServiceImpl(
      IRecordsMetadataRepository recordRepository,
      PatchOperationValidator patchOperationValidator,
      IEntitlementsAndCacheService entitlementsAndCacheService,
      StorageAuditLogger auditLogger, IPersistenceService persistenceService,
      DpsHeaders headers, RecordUtil recordUtil, Clock clock) {
    this.recordRepository = recordRepository;
    this.patchOperationValidator = patchOperationValidator;
    this.entitlementsAndCacheService = entitlementsAndCacheService;
    this.auditLogger = auditLogger;
    this.persistenceService = persistenceService;
    this.headers = headers;
    this.recordUtil = recordUtil;
    this.clock = clock;
  }

  @Override
  public BulkUpdateRecordsResponse bulkUpdateRecords(RecordBulkUpdateParam recordBulkUpdateParam, String user) {
    List<String> notFoundRecordIds = new ArrayList<>();
    List<String> unauthorizedRecordIds = new ArrayList<>();
    List<RecordMetadata> validRecordsMetadata = new ArrayList<>();
    List<String> validRecordsId = new ArrayList<>();
    List<String> lockedRecordsId = new ArrayList<>();

    RecordQuery bulkUpdateQuery = recordBulkUpdateParam.getQuery();
    List<PatchOperation> bulkUpdateOps = recordBulkUpdateParam.getOps();

    List<String> ids = bulkUpdateQuery.getIds();

    // validate record ids and properties
    recordUtil.validateRecordIds(ids);
    patchOperationValidator.validateOperations(bulkUpdateOps);

    //<idWithoutVersion, idWithVersion>
    Map<String, String> idMap = recordUtil.mapRecordsAndVersions(ids);
    List<String> idsWithoutVersion = new ArrayList<>(idMap.keySet());
    Map<String, RecordMetadata> existingRecords = recordRepository.get(idsWithoutVersion);

    final long currentTimestamp = clock.millis();
    for (String id : idsWithoutVersion) {
      String idWithVersion = idMap.get(id);
      RecordMetadata metadata = existingRecords.get(id);

      if (metadata == null) {
        notFoundRecordIds.add(idWithVersion);
        ids.remove(idWithVersion);
      } else {
        // pre acl check, enforce application data restriction
        boolean hasOwnerAccess = entitlementsAndCacheService.hasOwnerAccess(headers, metadata.getAcl().getOwners());
        if (hasOwnerAccess) {
          metadata = recordUtil.updateRecordMetaDataForPatchOperations(metadata, bulkUpdateOps, user, currentTimestamp);
          validRecordsMetadata.add(metadata);
          validRecordsId.add(id);
        } else {
          unauthorizedRecordIds.add(idWithVersion);
          ids.remove(idWithVersion);
        }
      }
    }

    if(!validRecordsId.isEmpty()) {
      lockedRecordsId = persistenceService.updateMetadata(validRecordsMetadata, validRecordsId, idMap);
    }

    for (String lockedId : lockedRecordsId) {
      ids.remove(lockedId);
    }

    BulkUpdateRecordsResponse recordsResponse = BulkUpdateRecordsResponse.builder()
        .notFoundRecordIds(notFoundRecordIds)
        .unAuthorizedRecordIds(unauthorizedRecordIds)
        .recordIds(ids)
        .lockedRecordIds(lockedRecordsId)
        .recordCount(ids.size()).build();

    auditCreateOrUpdateRecordsFails(recordsResponse);

    return recordsResponse;
  }

  private void auditCreateOrUpdateRecordsFails(BulkUpdateRecordsResponse recordsResponse) {
    List<String> failedUpdates =
        Stream.of(recordsResponse.getNotFoundRecordIds(), recordsResponse.getUnAuthorizedRecordIds(),
            recordsResponse.getLockedRecordIds()).flatMap(List::stream).collect(toList());
    if (!failedUpdates.isEmpty()) {
      auditLogger.createOrUpdateRecordsFail(failedUpdates);
    }
  }
}
