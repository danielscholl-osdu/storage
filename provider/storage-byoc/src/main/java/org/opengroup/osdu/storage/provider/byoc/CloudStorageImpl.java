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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.apache.commons.codec.binary.Base64.encodeBase64;

@Repository
public class CloudStorageImpl implements ICloudStorage {
    private static List<RecordProcessing> memList = new ArrayList<>();

    @Override
    public void write(RecordProcessing... recordsProcessing){
        for (RecordProcessing rp: recordsProcessing) {
            memList.add(rp);
        }
    }

    @Override
    public Map<String, String> getHash(Collection<RecordMetadata> records)
    {
        Gson gson = new Gson();
        Map<String, String> hashes = new HashMap<>();
        for (RecordMetadata rm : records)
        {
            String jsonData = this.read(rm, rm.getLatestVersion(), false);
            RecordData data = gson.fromJson(jsonData, RecordData.class);

            String hash = getHash(data);
            hashes.put(rm.getId(), hash);
        }
        return hashes;
    }

    @Override
    public boolean isDuplicateRecord(TransferInfo transfer, Map<String, String> hashMap, Map.Entry<RecordMetadata, RecordData> kv) {
        RecordMetadata updatedRecordMetadata = kv.getKey();
        RecordData recordData = kv.getValue();
        String recordHash = hashMap.get(updatedRecordMetadata.getId());

        String newHash = getHash(recordData);

        if (newHash.equals(recordHash)) {
            transfer.getSkippedRecords().add(updatedRecordMetadata.getId());
            return true;
        }else{
            return false;
        }
    }

    private String getHash(RecordData data) {
        Gson gson = new Gson();
        Crc32c checksumGenerator = new Crc32c();

        String newRecordStr = gson.toJson(data);
        byte[] bytes = newRecordStr.getBytes(StandardCharsets.UTF_8);
        checksumGenerator.update(bytes, 0, bytes.length);
        bytes = checksumGenerator.getValueAsBytes();
        String newHash = new String(encodeBase64(bytes));
        return newHash;
    }

    @Override
    public void delete(RecordMetadata record)
    {
        if (!record.hasVersion()) {
           return;
        }

        Iterator<RecordProcessing> it = memList.iterator();
        while(it.hasNext())
        {
            RecordProcessing rp = it.next();
            if (rp.getRecordMetadata().getId().equals(record.getId()))
            {
                it.remove();
            }
        }
    }

    @Override
    public void deleteVersion(RecordMetadata record, Long version) {
        //TODO: Check if this is correct

        Iterator<RecordProcessing> it = memList.iterator();
        while(it.hasNext())
        {
            RecordProcessing rp = it.next();
            if (rp.getRecordMetadata().getId().equals(record.getId())) {
                for (String path : rp.getRecordMetadata().getGcsVersionPaths()) {
                    if (path.contains(version.toString())) {
                        it.remove();
                    }
                }
            }
        }
    }

    @Override
    public boolean hasAccess(RecordMetadata... records)
    {
        for (RecordMetadata record : records)
        {
            if (!record.hasVersion()) {
                continue;
            }

            if (!record.getStatus().equals(RecordState.active))
                return false;
        }
        return true;
    }

    @Override
    public String read(RecordMetadata record, Long version, boolean checkDataInconsistency)
    {
        for (RecordProcessing rp : memList)
        {
            RecordMetadata rmd = rp.getRecordMetadata();
            if (rmd.getId().equals(record.getId()))
            {
                for (String path: rmd.getGcsVersionPaths())
                {
                    if (path.contains(version.toString())) {
                        try {
                            String result = new ObjectMapper().writeValueAsString(rp.getRecordData());
                            return result;
                        } catch (JsonProcessingException je)
                        {
                            throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found", je.getMessage());
                        }
                    }
                }
            }
        }
        String msg = String.format("Record with id '%s' does not exist", record.getId());
        throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found", msg);
    }

    @Override
    public Map<String, String> read(Map<String, String> objects)
    {
        Map<String, String> map = new HashMap<>();
        for (RecordProcessing rp : memList)
        {
            RecordMetadata rmd = rp.getRecordMetadata();
            String id = rmd.getId();
            if (objects.containsKey(id))
            {
                try {
                    String result = new ObjectMapper().writeValueAsString(rp.getRecordData());
                    map.put(id, result);
                } catch (JsonProcessingException je)
                {
                    throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Record to json failed", je.getMessage());
                }
            }
        }
        return map;
    }

}
