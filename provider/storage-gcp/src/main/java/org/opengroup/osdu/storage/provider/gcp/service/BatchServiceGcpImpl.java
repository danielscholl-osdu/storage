// Copyright 2017-2019, Schlumberger
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


package org.opengroup.osdu.storage.provider.gcp.service;

import com.google.cloud.datastore.DatastoreException;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.storage.service.BatchServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.opengroup.osdu.core.common.model.http.AppException;

import static java.util.Collections.singletonList;

@Service
public class BatchServiceGcpImpl extends BatchServiceImpl {

    @Autowired
    private StorageAuditLogger auditLogger;

    @Autowired
    private IQueryRepository queryRepository;


    @Override
    public DatastoreQueryResult getAllKinds(String cursor, Integer limit) {
        try {

            DatastoreQueryResult result = this.queryRepository.getAllKinds(limit, cursor);
            this.auditLogger.readAllKindsSuccess(result.getResults());
            return result;
        } catch (DatastoreException e) {
            // Is invalid cursor (code = 3) exception?
            if (e.getCode() == 3) {
                throw this.getInvalidCursorException();
            } else {
                throw this.getInternalErrorException();
            }
        } catch (IllegalStateException e) {
            throw this.getInvalidCursorException();
        }
    }

    @Override
    public DatastoreQueryResult getAllRecords(String cursor, String kind, Integer limit) {
        try {
            DatastoreQueryResult result = this.queryRepository.getAllRecordIdsFromKind(kind, limit,
                    cursor);
            if (!result.getResults().isEmpty()) {
                this.auditLogger.readAllRecordsOfGivenKindSuccess(singletonList(kind));
            }
            return result;
        } catch (DatastoreException e) {
            // Is invalid cursor (code = 3) exception?
            if (e.getCode() == 3) {
                throw this.getInvalidCursorException();
            } else {
                throw this.getInternalErrorException();
            }
        } catch (IllegalStateException e) {
            throw this.getInvalidCursorException();
        }
    }
    private AppException getInvalidCursorException() {
        return new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid",
                "The requested cursor does not exist or is invalid");
    }

    private AppException getInternalErrorException() {
        return new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal error", "An internal error occurred");
    }
}

