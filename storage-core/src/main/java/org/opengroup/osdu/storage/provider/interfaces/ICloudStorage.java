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

package org.opengroup.osdu.storage.provider.interfaces;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.NotImplementedException;

import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;


public interface ICloudStorage {

    static final String ACCESS_DENIED_ERROR_REASON = "Access denied";
    static final String ACCESS_DENIED_ERROR_MSG = "The user is not authorized to perform this action";

    void write(RecordProcessing... recordsProcessing);

    default Map<String, Acl> updateObjectMetadata(List<RecordMetadata> recordsMetadata, List<String> recordsId, List<RecordMetadata> validMetadata, List<String> lockedRecords, Map<String, String> recordsIdMap) {
        throw new NotImplementedException("TODO");
    }

    default void revertObjectMetadata(List<RecordMetadata> recordsMetadata, Map<String, Acl> originalAcls) {
        throw new NotImplementedException("TODO");
    }

    Map<String, String> getHash(Collection<RecordMetadata> records);

    void delete(RecordMetadata record);

    void deleteVersion(RecordMetadata record, Long version);

    boolean hasAccess(RecordMetadata... records);

    String read(RecordMetadata record, Long version, boolean checkDataInconsistency);

    Map<String, String> read(Map<String, String> objects);

    boolean isDuplicateRecord(TransferInfo transfer, Map<String, String> hashMap, Map.Entry<RecordMetadata, RecordData> kv);
}