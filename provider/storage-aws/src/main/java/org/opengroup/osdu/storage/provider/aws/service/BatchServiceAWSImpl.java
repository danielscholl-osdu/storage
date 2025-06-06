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

package org.opengroup.osdu.storage.provider.aws.service;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.aws.exceptions.InvalidCursorException;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.storage.service.BatchServiceImpl;
import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import java.util.Optional;

import static java.util.Collections.singletonList;

@Service
public class BatchServiceAWSImpl extends BatchServiceImpl {
    @Inject
    private StorageAuditLogger auditLogger;

    @Inject
    private IQueryRepository queryRepository;

    @Override
    public DatastoreQueryResult getAllKinds(String cursor, Integer limit)
    {
        try {
            DatastoreQueryResult result = this.queryRepository.getAllKinds(limit, cursor);
            this.auditLogger.readAllKindsSuccess(result.getResults());
            return result;
        } catch (InvalidCursorException e) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid", "The requested cursor does not exist or is invalid", e);
        } catch (Exception e) {
            throw this.getInternalErrorException(e);
        }
    }

    @Override
    public DatastoreQueryResult getAllRecords(String cursor, String kind, Integer limit, Optional<CollaborationContext> collaborationContext) {
        try {
            DatastoreQueryResult result = this.queryRepository.getAllRecordIdsFromKind(kind, limit, cursor, collaborationContext);
            if (!result.getResults().isEmpty()) {
                this.auditLogger.readAllRecordsOfGivenKindSuccess(singletonList(kind));
            }
            return result;
        } catch (InvalidCursorException e) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid", "The requested cursor does not exist or is invalid", e);
        } catch (Exception e) {
            throw this.getInternalErrorException(e);
        }
    }

    private AppException getInternalErrorException(Exception e) {
        return new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal error", e.getMessage());
    }
}
