// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.provider.byoc;

import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class QueryRepositoryImpl implements IQueryRepository {
    @Autowired
    private IRecordsMetadataRepository recordsMetadataRepository;

    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor)
    {
        List<String> kinds = new ArrayList<>();
        DatastoreQueryResult result = new DatastoreQueryResult();
        result.setCursor("");
        Collection<Map<String, String>> allKinds = SchemaRepositoryImpl.memMap.values();
        for (Map<String, String> kind: allKinds)
        {
            Iterator it = kind.keySet().iterator();
            kinds.add((String)it.next());
        }
        result.setResults(kinds);
        return result;
    }

    @Override
    public DatastoreQueryResult getAllRecordIdsFromKind(
            String kind, Integer limit, String cursor)
    {
        List<String> ids = new ArrayList<>();
        DatastoreQueryResult result = new DatastoreQueryResult();
        result.setCursor("");
        Set<String> recordIds = RecordsMetadataRepositoryImpl.memMap.keySet();
        for (String id: recordIds)
        {
            RecordMetadata rd = recordsMetadataRepository.get(id);
            if (rd.getKind().equals(kind)) {
                ids.add(id);
            }
        }
        result.setResults(ids);
        return result;
    }
}

