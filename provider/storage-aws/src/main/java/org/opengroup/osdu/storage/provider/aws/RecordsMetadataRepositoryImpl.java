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

package org.opengroup.osdu.storage.provider.aws;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.google.common.collect.Lists;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.LegalTagAssociationDoc;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.storage.util.JsonPatchUtil;
import jakarta.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@ConditionalOnProperty(prefix = "repository", name = "implementation", havingValue = "dynamodb",
        matchIfMissing = true)
@Repository
public class RecordsMetadataRepositoryImpl implements IRecordsMetadataRepository<String> {

    @Inject
    private DpsHeaders headers;

    @Inject
    private DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Inject
    private WorkerThreadPool workerThreadPool;
    @Inject
    private JaxRsDpsLog logger;

    @Value("${aws.dynamodb.recordMetadataTable.ssm.relativePath}")
    String recordMetadataTableParameterRelativePath;

    @Value("${aws.dynamodb.legalTagTable.ssm.relativePath}")
    String legalTagTableParameterRelativePath;

    private DynamoDBQueryHelperV2 getRecordMetadataQueryHelper() {
        return dynamoDBQueryHelperFactory.getQueryHelperForPartition(headers, recordMetadataTableParameterRelativePath, workerThreadPool.getClientConfiguration());
    }

    private DynamoDBQueryHelperV2 getLegalTagQueryHelper() {
        return dynamoDBQueryHelperFactory.getQueryHelperForPartition(headers, legalTagTableParameterRelativePath, workerThreadPool.getClientConfiguration());
    }

    private void addMetadataAndLegal(RecordMetadata metadata, Optional<CollaborationContext> collaborationContext, List<RecordMetadataDoc> metadataDocs,
                                     List<LegalTagAssociationDoc> createLegalDocs, List<LegalTagAssociationDoc> deleteLegalDocs) {
        // user should be part of the acl of the record being saved
        RecordMetadataDoc doc = new RecordMetadataDoc();

        // Set the core fields (what is expected in every implementation)
        doc.setId(CollaborationContextUtil.composeIdWithNamespace(metadata.getId(), collaborationContext));
        doc.setMetadata(metadata);
        // Add extra indexed fields for querying in DynamoDB
        doc.setKind(metadata.getKind());
        doc.setLegaltags(metadata.getLegal().getLegaltags());
        doc.setStatus(metadata.getStatus().name());
        doc.setUser(metadata.getUser());
        // Store the record to the database
        metadataDocs.add(doc);
        saveLegalTagAssociation(CollaborationContextUtil.composeIdWithNamespace(metadata.getId(), collaborationContext),
                                metadata.getLegal().getLegaltags(), createLegalDocs, deleteLegalDocs);
    }

    @Override
    public Map<String, String> patch(Map<RecordMetadata, JsonPatch> jsonPatchPerRecord, Optional<CollaborationContext> collaborationContext) {
        if (Objects.nonNull(jsonPatchPerRecord)) {
            List<RecordMetadataDoc> metadataDocs = new ArrayList<>();
            List<LegalTagAssociationDoc> legalDocs = new ArrayList<>();
            List<LegalTagAssociationDoc> deleteLegalDocs = new ArrayList<>();
            for (Entry<RecordMetadata, JsonPatch> recordEntry : jsonPatchPerRecord.entrySet()) {
                JsonPatch jsonPatch = recordEntry.getValue();
                RecordMetadata newRecordMetadata =
                        JsonPatchUtil.applyPatch(jsonPatch, RecordMetadata.class, recordEntry.getKey());

                addMetadataAndLegal(newRecordMetadata, collaborationContext, metadataDocs, legalDocs, deleteLegalDocs);
            }

            writeDynamoDBRecordsParallel(metadataDocs, legalDocs, deleteLegalDocs);
        }

        return new HashMap<>();
    }

    static final int MAX_DYNAMODB_READ_BATCH_SIZE = 100;

    static final int MAX_DYNAMODB_WRITE_BATCH_SIZE = 25;

    private <T> List<CompletableFuture<List<DynamoDBMapper.FailedBatch>>> createBatchedFutures(List<T> objects, DynamoDBQueryHelperV2 dynamomDbHelper) {
        return Lists.partition(objects, MAX_DYNAMODB_WRITE_BATCH_SIZE)
                    .stream()
                    .map(objectBatch -> CompletableFuture.supplyAsync(
                        () -> dynamomDbHelper.batchSave(objectBatch), workerThreadPool.getThreadPool())
                    ).toList();
    }

    private void writeDynamoDBRecordsParallel(List<RecordMetadataDoc> metadataDocs, List<LegalTagAssociationDoc> createLegalDocs, List<LegalTagAssociationDoc> deleteLegalDocs) {
        DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        DynamoDBQueryHelperV2 legalTagHelper = getLegalTagQueryHelper();
        List<CompletableFuture<List<DynamoDBMapper.FailedBatch>>> batchWriteProcesses = Lists.newArrayList(createBatchedFutures(metadataDocs, recordMetadataQueryHelper));
        batchWriteProcesses.addAll(createBatchedFutures(createLegalDocs, legalTagHelper));
        batchWriteProcesses.addAll(Lists.partition(deleteLegalDocs, MAX_DYNAMODB_WRITE_BATCH_SIZE)
                    .stream()
                    .map(objectBatch -> CompletableFuture.supplyAsync(
                        () -> legalTagHelper.batchDelete(objectBatch), workerThreadPool.getThreadPool())
                    ).toList());

        CompletableFuture[] cfs = batchWriteProcesses.toArray(new CompletableFuture[0]);
        CompletableFuture<List<DynamoDBMapper.FailedBatch>> jointFutures = CompletableFuture.allOf(cfs)
                                                                                            .thenApply(ignored -> batchWriteProcesses.stream()
                                                                                                                                     .map(CompletableFuture::join)
                                                                                                                                     .flatMap(List::stream)
                                                                                                                                     .toList());
        List<DynamoDBMapper.FailedBatch> failed = null;
        try {
            failed = jointFutures.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unknown error", "Could not collect thread futures.", e);
        }
        Exception firstFailedException = null;
        for (DynamoDBMapper.FailedBatch failedDoc : failed) {
            logger.error("Failed to save some objects to DynamoDB", failedDoc.getException());
            if (firstFailedException == null && failedDoc.getException() != null) {
                firstFailedException = failedDoc.getException();
            }
            for (Entry<String, List<WriteRequest>> failedEntry : failedDoc.getUnprocessedItems().entrySet()) {
                for (WriteRequest failedWriteRequest : failedEntry.getValue()) {
                    logger.error(String.format("Failed to save DynamoDB Object %s to Table %s", failedWriteRequest.getPutRequest(), failedEntry.getKey()));
                }
            }
        }
        if (firstFailedException != null) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unknown error", "Could not save record metadata", firstFailedException);
        }
    }

    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata, Optional<CollaborationContext> collaborationContext) {
        if (recordsMetadata != null) {
            List<RecordMetadataDoc> metadataDocs = new ArrayList<>();
            List<LegalTagAssociationDoc> createLegalDocs = new ArrayList<>();
            List<LegalTagAssociationDoc> deleteLegalDocs = new ArrayList<>();
            recordsMetadata.forEach(recordMetadata -> addMetadataAndLegal(recordMetadata, collaborationContext, metadataDocs, createLegalDocs, deleteLegalDocs));

            writeDynamoDBRecordsParallel(metadataDocs, createLegalDocs, deleteLegalDocs);
        }
        return recordsMetadata;
    }

    @Override
    public void delete(String id, Optional<CollaborationContext> collaborationContext) {
        DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        RecordMetadataDoc rmdItem = new RecordMetadataDoc();
        String recordId = CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext);
        rmdItem.setId(recordId);
        recordMetadataQueryHelper.deleteByObject(rmdItem);
        DynamoDBQueryHelperV2 ltaQueryHelper = getLegalTagQueryHelper();
        LegalTagAssociationDoc queryObject = new LegalTagAssociationDoc();
        queryObject.setRecordId(recordId);
        List<LegalTagAssociationDoc> legalTagAssociationDocs = ltaQueryHelper.queryByGSI(LegalTagAssociationDoc.class, queryObject);
        writeDynamoDBRecordsParallel(Collections.emptyList(), Collections.emptyList(), legalTagAssociationDocs);
    }

    @Override
    public RecordMetadata get(String id, Optional<CollaborationContext> collaborationContext) {
        DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        RecordMetadataDoc doc = recordMetadataQueryHelper.loadByPrimaryKey(RecordMetadataDoc.class,
                CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext));
        if (doc == null) {
            return null;
        } else {
            return doc.getMetadata();
        }
    }

    @Override
    public Map<String, RecordMetadata> get(List<String> ids, Optional<CollaborationContext> collaborationContext) {

        DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();

        Map<String, RecordMetadata> output = new HashMap<>();
        Set<String> filteredIds = ids.stream().map(id -> CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext)).collect(Collectors.toSet());
        Lists.partition(filteredIds.stream().toList(), MAX_DYNAMODB_READ_BATCH_SIZE)
             .stream()
             .map(recordIds -> recordMetadataQueryHelper.batchLoadByPrimaryKey(RecordMetadataDoc.class, new HashSet<>(recordIds)))
             .flatMap(List::stream)
             .filter(Objects::nonNull)
             .forEach(rmd -> output.put(rmd.getId(), rmd.getMetadata()));
        return output;
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit) {
        return null;
    }

    //replace with the new method queryByLegal
    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
            String legalTagName, int limit, String cursor) {

        DynamoDBQueryHelperV2 legalTagQueryHelper = getLegalTagQueryHelper();

        LegalTagAssociationDoc legalTagAssociationDoc = new LegalTagAssociationDoc();
        legalTagAssociationDoc.setLegalTag(legalTagName);
        QueryPageResult<LegalTagAssociationDoc> result = null;
        try {
            result = legalTagQueryHelper.queryPage(LegalTagAssociationDoc.class,
                    legalTagAssociationDoc, 500, cursor);
        } catch (UnsupportedEncodingException e) {
            throw new AppException(org.apache.http.HttpStatus.SC_BAD_REQUEST, "Problem querying for legal tag", e.getMessage());
        }

        List<String> associatedRecordIds = new ArrayList<>();
        result.results.forEach(doc -> associatedRecordIds.add(doc.getRecordId())); // extract the Kinds from the SchemaDocs

        List<RecordMetadata> associatedRecords = new ArrayList<>();
        for(String recordId : associatedRecordIds){
            associatedRecords.add(get(recordId, Optional.empty()));
        }

        return new AbstractMap.SimpleEntry<>(result.cursor, associatedRecords);
    }

    private void saveLegalTagAssociation(String recordId, Set<String> legalTags, List<LegalTagAssociationDoc> createLegalTags, List<LegalTagAssociationDoc> deleteLegalTags){
        DynamoDBQueryHelperV2 legalTagHelper = getLegalTagQueryHelper();
        LegalTagAssociationDoc queryDoc = new LegalTagAssociationDoc();
        queryDoc.setRecordId(recordId);
        List<LegalTagAssociationDoc> currentDocs = legalTagHelper.queryByGSI(LegalTagAssociationDoc.class, queryDoc);
        Set<String> existingLegalTags = currentDocs.stream().map(LegalTagAssociationDoc::getLegalTag).collect(Collectors.toSet());
        deleteLegalTags.addAll(existingLegalTags.stream().filter(lt -> !legalTags.contains(lt)).map(lt -> LegalTagAssociationDoc.createLegalTagDoc(lt, recordId)).toList());
        createLegalTags.addAll(legalTags.stream().filter(lt -> !existingLegalTags.contains(lt)).map(lt -> LegalTagAssociationDoc.createLegalTagDoc(lt, recordId)).toList());
    }
}
