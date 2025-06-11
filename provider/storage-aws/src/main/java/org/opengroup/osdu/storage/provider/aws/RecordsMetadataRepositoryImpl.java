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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.aws.v2.dynamodb.model.GsiQueryRequest;
import org.opengroup.osdu.core.aws.v2.dynamodb.model.QueryPageResult;
import org.opengroup.osdu.core.aws.v2.dynamodb.util.RequestBuilderUtil;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.LegalTagAssociationDoc;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.util.JsonPatchUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import com.github.fge.jsonpatch.JsonPatch;
import com.google.common.collect.Lists;

import jakarta.inject.Inject;

@ConditionalOnProperty(prefix = "repository", name = "implementation", havingValue = "dynamodb", matchIfMissing = true)
@Repository
public class RecordsMetadataRepositoryImpl implements IRecordsMetadataRepository<String> {

    private final DpsHeaders headers;
    private final DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;
    private final WorkerThreadPool workerThreadPool;
    private final JaxRsDpsLog logger;
    private static final String LEGAL_GSI_RECORDID_INDEX_NAME = "recordId-index";
    private static final String LEGAL_GSI_LEGALTAG_INDEX_NAME = "legalTag-index";

    @Inject
    public RecordsMetadataRepositoryImpl(DpsHeaders headers, DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory,
                                         WorkerThreadPool workerThreadPool, JaxRsDpsLog logger) {
        this.headers = headers;
        this.dynamoDBQueryHelperFactory = dynamoDBQueryHelperFactory;
        this.workerThreadPool = workerThreadPool; 
        this.logger = logger;
    }

    private static final String UNKNOWN_ERROR = "Unknown error";

    @Value("${aws.dynamodb.recordMetadataTable.ssm.relativePath}")
    String recordMetadataTableParameterRelativePath;

    @Value("${aws.dynamodb.legalTagTable.ssm.relativePath}")
    String legalTagTableParameterRelativePath;

    private DynamoDBQueryHelper<RecordMetadataDoc> getRecordMetadataQueryHelper() {
        return dynamoDBQueryHelperFactory.createQueryHelper(headers, recordMetadataTableParameterRelativePath, RecordMetadataDoc.class);
    }

    private DynamoDBQueryHelper<LegalTagAssociationDoc> getLegalTagQueryHelper() {
        return dynamoDBQueryHelperFactory.createQueryHelper(headers, legalTagTableParameterRelativePath, LegalTagAssociationDoc.class);
    }

    private void addMetadataAndLegal(RecordMetadata metadata, Optional<CollaborationContext> collaborationContext,
            List<RecordMetadataDoc> metadataDocs,
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
    public Map<String, String> patch(Map<RecordMetadata, JsonPatch> jsonPatchPerRecord,
            Optional<CollaborationContext> collaborationContext) {
        if (Objects.nonNull(jsonPatchPerRecord)) {
            List<RecordMetadataDoc> metadataDocs = new ArrayList<>();
            List<LegalTagAssociationDoc> legalDocs = new ArrayList<>();
            List<LegalTagAssociationDoc> deleteLegalDocs = new ArrayList<>();
            for (Entry<RecordMetadata, JsonPatch> recordEntry : jsonPatchPerRecord.entrySet()) {
                JsonPatch jsonPatch = recordEntry.getValue();
                RecordMetadata newRecordMetadata = JsonPatchUtil.applyPatch(jsonPatch, RecordMetadata.class,
                        recordEntry.getKey());

                addMetadataAndLegal(newRecordMetadata, collaborationContext, metadataDocs, legalDocs, deleteLegalDocs);
            }

            writeDynamoDBRecordsParallel(metadataDocs, legalDocs, deleteLegalDocs);
        }

        return new HashMap<>();
    }

    static final int MAX_DYNAMODB_READ_BATCH_SIZE = 100;

    static final int MAX_DYNAMODB_WRITE_BATCH_SIZE = 25;

    private <T> List<CompletableFuture<BatchWriteResult>> createBatchedFutures(List<T> objects,
                                                                                     DynamoDBQueryHelper<T> dynamomDbHelper) {
        return Lists.partition(objects, MAX_DYNAMODB_WRITE_BATCH_SIZE)
                .stream()
                .map(objectBatch -> CompletableFuture.supplyAsync(
                        () -> dynamomDbHelper.batchSave(objectBatch), workerThreadPool.getThreadPool()))
                .toList();
    }

    private void writeDynamoDBRecordsParallel(List<RecordMetadataDoc> metadataDocs,
            List<LegalTagAssociationDoc> createLegalDocs, List<LegalTagAssociationDoc> deleteLegalDocs) {
        DynamoDBQueryHelper<RecordMetadataDoc> recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        DynamoDBQueryHelper<LegalTagAssociationDoc> legalTagHelper = getLegalTagQueryHelper();
        List<CompletableFuture<BatchWriteResult>> batchWriteProcesses = Lists
                .newArrayList(createBatchedFutures(metadataDocs, recordMetadataQueryHelper));

        batchWriteProcesses.addAll(createBatchedFutures(createLegalDocs, legalTagHelper));
        batchWriteProcesses.addAll(Lists.partition(deleteLegalDocs, MAX_DYNAMODB_WRITE_BATCH_SIZE)
                .stream()
                .map(objectBatch -> CompletableFuture.supplyAsync(
                        () -> legalTagHelper.batchDelete(objectBatch), workerThreadPool.getThreadPool())
                ).toList());
        CompletableFuture<?>[] cfs = batchWriteProcesses.toArray(CompletableFuture[]::new);
        CompletableFuture<List<BatchWriteResult>> jointFutures = CompletableFuture.allOf(cfs)
                .thenApply(ignored -> batchWriteProcesses.stream()
                        .map(CompletableFuture::join)
                        .toList());
        List<BatchWriteResult> results = null;
        boolean batchFailed = false;
        try {
            results = jointFutures.get().stream().toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR,
                    "Could not collect thread futures.", e);
        } catch (ExecutionException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR,
                    "Could not collect thread futures.", e);
        }

        for (BatchWriteResult batchResult : results) {
            for (RecordMetadataDoc failedRecordMetadataDoc: batchResult.unprocessedPutItemsForTable(recordMetadataQueryHelper.getTable())){
                batchFailed = true;
                logger.error(String.format("Failed to save RecordMetadata Object %s to Table %s", failedRecordMetadataDoc.getId(), recordMetadataQueryHelper.getTable().tableName()));
            }
            for (LegalTagAssociationDoc failedLegalTagAssociationDoc: batchResult.unprocessedPutItemsForTable(legalTagHelper.getTable())){
                batchFailed = true;
                logger.error(String.format("Failed to save LegalTagAssociation Object %s to Table %s", failedLegalTagAssociationDoc.getRecordIdLegalTag(), legalTagHelper.getTable().tableName()));
            }
        }
        if (batchFailed) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, UNKNOWN_ERROR,
                    "Could not save record metadata");
        }
    }

    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata,
            Optional<CollaborationContext> collaborationContext) {
        if (recordsMetadata != null) {
            List<RecordMetadataDoc> metadataDocs = new ArrayList<>();
            List<LegalTagAssociationDoc> createLegalDocs = new ArrayList<>();
            List<LegalTagAssociationDoc> deleteLegalDocs = new ArrayList<>();
            recordsMetadata.forEach(recordMetadata -> addMetadataAndLegal(recordMetadata, collaborationContext,
                    metadataDocs, createLegalDocs, deleteLegalDocs));

            writeDynamoDBRecordsParallel(metadataDocs, createLegalDocs, deleteLegalDocs);
        }
        return recordsMetadata;
    }

    @Override
    public void delete(String id, Optional<CollaborationContext> collaborationContext) {
        DynamoDBQueryHelper<RecordMetadataDoc> recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        RecordMetadataDoc rmdItem = new RecordMetadataDoc();
        String recordId = CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext);
        rmdItem.setId(recordId);
        recordMetadataQueryHelper.deleteItem(rmdItem);
        DynamoDBQueryHelper<LegalTagAssociationDoc> ltaQueryHelper = getLegalTagQueryHelper();
        LegalTagAssociationDoc queryObject = new LegalTagAssociationDoc();
        queryObject.setRecordId(recordId);
        GsiQueryRequest<LegalTagAssociationDoc> queryRequest =
                RequestBuilderUtil.QueryRequestBuilder.forQuery(queryObject, LEGAL_GSI_RECORDID_INDEX_NAME, LegalTagAssociationDoc.class)
                        .buildGsiRequest();
        QueryPageResult<LegalTagAssociationDoc> legalTagAssociationDocs = ltaQueryHelper.queryByGSI(queryRequest, true);
        writeDynamoDBRecordsParallel(Collections.emptyList(), Collections.emptyList(), legalTagAssociationDocs.getItems());
    }

    @Override
    public RecordMetadata get(String id, Optional<CollaborationContext> collaborationContext) {
        DynamoDBQueryHelper<RecordMetadataDoc> recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        Optional<RecordMetadataDoc> docOptional = recordMetadataQueryHelper.getItem(CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext));
        if (docOptional.isEmpty()) {
            return null;
        } else {
            return docOptional.get().getMetadata();
        }
    }

    @Override
    public Map<String, RecordMetadata> get(List<String> ids, Optional<CollaborationContext> collaborationContext) {

        DynamoDBQueryHelper<RecordMetadataDoc> recordMetadataQueryHelper = getRecordMetadataQueryHelper();

        Map<String, RecordMetadata> output = new HashMap<>();
        Set<String> filteredIds = ids.stream()
                .map(id -> CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext))
                .collect(Collectors.toSet());
        Lists.partition(filteredIds.stream().toList(), MAX_DYNAMODB_READ_BATCH_SIZE)
                .stream()
                .map(recordIds -> recordMetadataQueryHelper.batchLoadByPrimaryKey(new HashSet<>(recordIds)))
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .forEach(rmd -> output.put(rmd.getId(), rmd.getMetadata()));
        return output;
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName,
            LegalCompliance status, int limit) {
        return null;
    }
    
    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
            String[] legalTagName, int limit, String cursor) {
        throw new UnsupportedOperationException("Method not implemented.");
    }

    // replace with the new method queryByLegal
    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
            String legalTagName, int limit, String cursor) {

        DynamoDBQueryHelper<LegalTagAssociationDoc> legalTagQueryHelper = getLegalTagQueryHelper();

        LegalTagAssociationDoc legalTagAssociationDoc = new LegalTagAssociationDoc();
        legalTagAssociationDoc.setLegalTag(legalTagName);
        QueryPageResult<LegalTagAssociationDoc> result = null;
        try {
            GsiQueryRequest<LegalTagAssociationDoc> request =
                    RequestBuilderUtil.QueryRequestBuilder.forQuery(legalTagAssociationDoc, LEGAL_GSI_LEGALTAG_INDEX_NAME, LegalTagAssociationDoc.class)
                            .limit(limit)
                            .cursor(cursor)
                            .buildGsiRequest();
            result = legalTagQueryHelper.queryByGSI(request, false);
        } catch (IllegalArgumentException e) {
            throw new AppException(org.apache.http.HttpStatus.SC_BAD_REQUEST, "Problem querying for legal tag",
                    e.getMessage());
        }

        List<String> associatedRecordIds = new ArrayList<>();
        result.getItems().forEach(doc -> associatedRecordIds.add(doc.getRecordId())); // extract the Kinds from the
                                                                                   // SchemaDocs

        List<RecordMetadata> associatedRecords = new ArrayList<>();
        for (String recordId : associatedRecordIds) {
            associatedRecords.add(get(recordId, Optional.empty()));
        }

        return new AbstractMap.SimpleEntry<>(result.getNextCursor(), associatedRecords);
    }

    private void saveLegalTagAssociation(String recordId, Set<String> legalTags,
            List<LegalTagAssociationDoc> createLegalTags, List<LegalTagAssociationDoc> deleteLegalTags) {
        DynamoDBQueryHelper<LegalTagAssociationDoc> legalTagHelper = getLegalTagQueryHelper();
        LegalTagAssociationDoc queryDoc = new LegalTagAssociationDoc();
        queryDoc.setRecordId(recordId);
        GsiQueryRequest<LegalTagAssociationDoc> queryRequest =
                RequestBuilderUtil.QueryRequestBuilder.forQuery(queryDoc, LEGAL_GSI_RECORDID_INDEX_NAME, LegalTagAssociationDoc.class)
                        .buildGsiRequest();
        QueryPageResult<LegalTagAssociationDoc> currentDocs = legalTagHelper.queryByGSI(queryRequest, true);
        Set<String> existingLegalTags = currentDocs.getItems().stream().map(LegalTagAssociationDoc::getLegalTag)
                .collect(Collectors.toSet());
        deleteLegalTags.addAll(existingLegalTags.stream().filter(lt -> !legalTags.contains(lt))
                .map(lt -> LegalTagAssociationDoc.createLegalTagDoc(lt, recordId)).collect(Collectors.toList()));
        createLegalTags.addAll(legalTags.stream().filter(lt -> !existingLegalTags.contains(lt))
                .map(lt -> LegalTagAssociationDoc.createLegalTagDoc(lt, recordId)).collect(Collectors.toList()));
    }
}
