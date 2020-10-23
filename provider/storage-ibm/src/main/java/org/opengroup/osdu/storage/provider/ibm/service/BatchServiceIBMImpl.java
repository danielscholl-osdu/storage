/**
 * Copyright 2020 IBM Corp. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.ibm.service;

import static java.util.Collections.singletonList;

import javax.inject.Inject;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.opengroup.osdu.storage.service.BatchServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BatchServiceIBMImpl extends BatchServiceImpl {

	@Inject
    private StorageAuditLogger auditLogger;

	@Inject
    private IQueryRepository queryRepository;

    @Override
    public DatastoreQueryResult getAllKinds(String cursor, Integer limit)
    {
        DatastoreQueryResult result = this.queryRepository.getAllKinds(limit, cursor);
        this.auditLogger.readAllKindsSuccess(result.getResults());
        return result;
    }

    @Override
    public DatastoreQueryResult getAllRecords(String cursor, String kind, Integer limit)
    {
        DatastoreQueryResult result = this.queryRepository.getAllRecordIdsFromKind(kind, limit, cursor);
        if (!result.getResults().isEmpty()) {
            this.auditLogger.readAllRecordsOfGivenKindSuccess(singletonList(kind));
        }
        return result;
    }

}
