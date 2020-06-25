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

import com.google.gson.Gson;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class RecordsMetadataRepositoryImpl implements IRecordsMetadataRepository<String> {
    //record id, <meta name, meta value>
    public static Map<String, String> memMap = new HashMap<>();

    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata)
    {
        if (recordsMetadata != null) {
            for (RecordMetadata recordMetadata : recordsMetadata) {
                String recordEntity = this.createRecordEntity(recordMetadata);
                memMap.put(recordMetadata.getId(), recordEntity);
            }
        }
        return recordsMetadata;
    }

    @Override
    public void delete(String id)
    {
        memMap.remove(id);
    }

    @Override
    public RecordMetadata get(String id)
    {
        String entityJson = memMap.get(id);
        if (entityJson == null) return null;
        return this.parseEntityToRecordMetadata(entityJson);
    }

    @Override
    public Map<String, RecordMetadata> get(List<String> ids)
    {
        Map<String, RecordMetadata> output = new HashMap<>();

        for (int i = 0; i < ids.size(); i++) {
            String key = ids.get(i);
            RecordMetadata rmd = this.get(key);
            if (rmd == null) continue;
            output.put(key, rmd);
        }

        return output;
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit) {
        return null;
    }

    //TODO replace with the new method queryByLegal
    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
            String legalTagName, int limit, String cursor)
    {
        List<RecordMetadata> outputRecords = new ArrayList<>();
        Iterator it = memMap.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry pair = (Map.Entry) it.next();
            String key = pair.getKey().toString();
            RecordMetadata rmd = this.get(key);
            if (rmd.getLegal().getLegaltags().contains(legalTagName))
                outputRecords.add(rmd);
        }

        return new AbstractMap.SimpleEntry<>("", outputRecords);
    }

    private RecordMetadata parseEntityToRecordMetadata(String entityJson) {
        Gson gson = new Gson();

        RecordMetadata recordMetadata = null;
        if (entityJson != null) {
            recordMetadata = gson.fromJson(entityJson, RecordMetadata.class);
        }

        return recordMetadata;
    }

    private String createRecordEntity(RecordMetadata record) {
        Gson gson = new Gson();

        return gson.toJson(record);
    }
}

