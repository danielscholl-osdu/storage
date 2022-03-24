// Copyright © Schlumberger
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

package org.opengroup.osdu.storage.opa.service;

import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import java.util.List;
import java.util.Map;

public interface IOPAService {
    /**
     * This method is to call opa service to get the result of data authorization.
     * @param recordsMetadata contains id, acl, legal information in it.
     * @param operationType specifies the type of operation, i.e. view, update, delete, etc.
     * @return a list of ValidationOutputRecord with record id and authorization errors in it.
     */
    List<ValidationOutputRecord> validateUserAccessToRecords(List<RecordMetadata> recordsMetadata, OperationType operationType);
}
