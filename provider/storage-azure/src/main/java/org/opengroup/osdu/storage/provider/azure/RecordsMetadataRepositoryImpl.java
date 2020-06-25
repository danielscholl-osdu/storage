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

package org.opengroup.osdu.storage.provider.azure;

import com.azure.data.cosmos.CosmosClientException;
import com.microsoft.azure.spring.data.cosmosdb.core.query.DocumentDbPageRequest;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class RecordsMetadataRepositoryImpl implements IRecordsMetadataRepository<String> {
    @Autowired
    private CosmosDBRecord db;

    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata) {
        if (recordsMetadata != null) {
            for (RecordMetadata recordMetadata : recordsMetadata) {
                RecordMetadataDoc doc = new RecordMetadataDoc();
                doc.setId(recordMetadata.getId());
                doc.setMetadata(recordMetadata);
                db.save(doc);
            }
        }
        return recordsMetadata;
    }

    @Override
    public void delete(String id) {
        RecordMetadataDoc doc = new RecordMetadataDoc();
        doc.setId(id);
        db.delete(doc);
    }

    @Override
    public RecordMetadata get(String id) {
        Optional<RecordMetadataDoc> doc = db.findById(id);
        if (!doc.isPresent())
            return null;

        return doc.get().getMetadata();
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
        String continuation = null;

        Iterable<RecordMetadataDoc> docs;

        try {
            //TODO:
            // array_contains isn't supported in cosmosdb Spring JPA yet
            // fetching all records which absolutely should be fixed before going prod!
            final Page<RecordMetadataDoc> docPage = db.findAll(DocumentDbPageRequest.of(0, limit, cursor));
            docs = docPage.getContent();
            docs.forEach(d -> {
                if (d.getMetadata().getLegal().getLegaltags().contains(legalTagName))
                    outputRecords.add(d.getMetadata());
            });

            Pageable pageable = docPage.getPageable();
            continuation = ((DocumentDbPageRequest) pageable).getRequestContinuation();
        } catch (Exception e) {
            if (e.getCause() instanceof CosmosClientException) {
                CosmosClientException ce = (CosmosClientException) e.getCause();
                if(ce.statusCode() == HttpStatus.SC_BAD_REQUEST && ce.getMessage().contains("Invalid Continuation Token"))
                    throw this.getInvalidCursorException();
            }
        }

        return new AbstractMap.SimpleEntry<>(continuation, outputRecords);
    }

    @Override
    public Map<String, RecordMetadata> get(List<String> ids) {
        Map<String, RecordMetadata> output = new HashMap<>();

        for (String id: ids) {
            Optional<RecordMetadataDoc> doc = db.findById(id);
            if (!doc.isPresent()) continue;
            RecordMetadata rmd = doc.get().getMetadata();
            if (rmd == null) continue;
            output.put(id, rmd);
        }

        return output;
    }

    private AppException getInvalidCursorException() {
        return new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid",
                "The requested cursor does not exist or is invalid");
    }
}
