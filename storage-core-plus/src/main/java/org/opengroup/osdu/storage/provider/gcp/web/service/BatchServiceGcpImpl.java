/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
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

package org.opengroup.osdu.storage.provider.gcp.web.service;

import static java.util.Collections.singletonList;

import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.opengroup.osdu.storage.service.BatchServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BatchServiceGcpImpl extends BatchServiceImpl {

    private final IQueryRepository queryRepository;
    private final StorageAuditLogger auditLogger;

    @Override
    public DatastoreQueryResult getAllKinds(String cursor, Integer limit) {
        DatastoreQueryResult result = this.queryRepository.getAllKinds(limit, cursor);
        this.auditLogger.readAllKindsSuccess(result.getResults());
        return result;
    }

    @Override
    public DatastoreQueryResult getAllRecords(String cursor, String kind, Integer limit, Optional<CollaborationContext> collaborationContext) {
        DatastoreQueryResult result = this.queryRepository.getAllRecordIdsFromKind(kind, limit, cursor, collaborationContext);
        if (!result.getResults().isEmpty()) {
            this.auditLogger.readAllRecordsOfGivenKindSuccess(singletonList(kind));
        }
        return result;
    }
}
