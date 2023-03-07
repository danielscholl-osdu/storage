package org.opengroup.osdu.storage.provider.azure.repository;

import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.azure.cosmosdb.CosmosStore;
import org.opengroup.osdu.azure.cosmosdb.CosmosStoreBulkOperations;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.azure.RecordMetadataDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RecordMetadataRepositoryTest {
    private final static String RECORD_ID1 = "opendes:id1:15706318658560";
    private final static String RECORD_ID2 = "opendes:id2:15706318658560";
    private final static String KIND = "opendes:source:type:1.0.0";
    private final static String STATUS = "active";

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private CosmosStoreBulkOperations cosmosBulkStore;

    @Mock
    private DpsHeaders headers;

    @Mock
    private Page<RecordMetadataDoc> page;

    @Mock
    private CosmosStore operation;

    @InjectMocks
    private RecordMetadataRepository recordMetadataRepository;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        when(headers.getPartitionId()).thenReturn("opendes");
        ReflectionTestUtils.setField(recordMetadataRepository, "cosmosDBName", "osdu-db");
        ReflectionTestUtils.setField(recordMetadataRepository, "recordMetadataCollection", "collection");
        ReflectionTestUtils.setField(recordMetadataRepository, "minBatchSizeToUseBulkUpload", 2);
    }


    @Test
    public void shouldFailOnCreateOrUpdate_IfAclIsNull() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Acl of the record must not be null");
        try {
            recordMetadataRepository.createOrUpdate(singletonList(new RecordMetadata()), Optional.empty());
        } catch (IllegalArgumentException e) {
            verify(logger, only()).error("Acl of the record RecordMetadata(id=null, kind=null, previousVersionKind=null, acl=null, legal=null, ancestry=null, tags={}, gcsVersionPaths=[], status=null, user=null, createTime=0, modifyUser=null, modifyTime=0, hash=null) must not be null");
        }

    }

    @Test
    public void shouldSetCorrectDocId_IfCollaborationContextIsProvided_InParallel() {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).build();
        RecordMetadata recordMetadata1 = createRecord(RECORD_ID1);
        RecordMetadata recordMetadata2 = createRecord(RECORD_ID2);
        List<RecordMetadata> recordMetadataList = new ArrayList<RecordMetadata>() {{
            add(recordMetadata1);
            add(recordMetadata2);
        }};
        recordMetadataRepository.createOrUpdate(recordMetadataList, Optional.of(collaborationContext));

        ArgumentCaptor<List> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(cosmosBulkStore).bulkInsertWithCosmosClient(any(), any(), any(), docCaptor.capture(), any(), eq(1));
        List capturedDocs = docCaptor.getValue();
        RecordMetadataDoc capturedDoc1 = (RecordMetadataDoc) capturedDocs.get(0);
        assertEquals(capturedDoc1.getId(), CollaborationId.toString() + RECORD_ID1);
        RecordMetadataDoc capturedDoc2 = (RecordMetadataDoc) capturedDocs.get(1);
        assertEquals(capturedDoc2.getId(), CollaborationId.toString() + RECORD_ID2);
    }

    @Test
    public void shouldSetCorrectDocId_IfCollaborationContextIsProvided_InSerial() {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).build();
        
        String expectedDocId = CollaborationId + RECORD_ID1;
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);
        recordMetadataRepository.createOrUpdate(singletonList(recordMetadata), Optional.of(collaborationContext));

        ArgumentCaptor<RecordMetadataDoc> itemCaptor = ArgumentCaptor.forClass(RecordMetadataDoc.class);
        verify(operation).upsertItem(any(),
                any(),
                eq("collection"),
                eq(CollaborationId.toString() + RECORD_ID1),
                itemCaptor.capture());
        
        RecordMetadataDoc capturedItem = itemCaptor.getValue();
        System.out.println("jh");
        assertEquals(expectedDocId, capturedItem.getId());
    }

    @Test
    public void shouldPatchRecordsWithCorrectDocId_whenCollaborationContextIsProvided() throws IOException {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).build();
        String expectedDocId = CollaborationId + RECORD_ID1;
        List<String> docIds = new ArrayList<>();
        docIds.add(expectedDocId);
        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);
        recordMetadataList.add(recordMetadata);
        List<String> partitionKeys = new ArrayList<>();
        partitionKeys.add(RECORD_ID1);
        recordMetadataRepository.patch(recordMetadataList, getJsonPatchFromJsonString(getValidInputJsonForPatch()), Optional.of(collaborationContext));
        verify(cosmosBulkStore, times(1)).bulkPatchWithCosmosClient(eq("opendes"), eq("osdu-db"), eq("collection"), eq(docIds), any(CosmosPatchOperations.class), eq(partitionKeys), eq(1));
    }

    @Test
    public void shouldPatchRecordsWithCorrectDocId_whenCollaborationContextIsNotProvided() throws IOException {
        List<String> docIds = new ArrayList<>();
        docIds.add(RECORD_ID1);
        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);
        recordMetadataList.add(recordMetadata);
        List<String> partitionKeys = new ArrayList<>();
        partitionKeys.add(RECORD_ID1);
        recordMetadataRepository.patch(recordMetadataList, getJsonPatchFromJsonString(getValidInputJsonForPatch()), Optional.empty());
        verify(cosmosBulkStore, times(1)).bulkPatchWithCosmosClient(eq("opendes"), eq("osdu-db"), eq("collection"), eq(docIds), any(CosmosPatchOperations.class), eq(partitionKeys), eq(1));
    }

    @Test
    public void shouldQueryByDocIdWithCollaborationId_IfCollaborationContextIsProvided() {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).build();
        String expectedQuery = "SELECT c.metadata.id FROM c WHERE c.metadata.kind = '" + KIND + "' AND c.metadata.status = 'active' and STARTSWITH(c.id, '" + CollaborationId.toString() + "') ";

        Pageable pageable = PageRequest.of(0, 8);

        doReturn(page).when(operation).queryItemsPage(any(),
                any(),
                eq("opendes"),
                any(SqlQuerySpec.class),
                any(Class.class),
                eq(8),
                any());

        this.recordMetadataRepository.findIdsByMetadata_kindAndMetadata_status(KIND, STATUS, pageable, Optional.of(collaborationContext));

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        verify(operation).queryItemsPage(any(),
                any(),
                eq("opendes"),
                queryCaptor.capture(),
                any(Class.class),
                eq(8),
                any());
        SqlQuerySpec capturedQuery = queryCaptor.getValue();
        assertEquals(expectedQuery, capturedQuery.getQueryText());
    }

    private RecordMetadata createRecord(String recordId) {
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId(recordId);
        Acl recordAcl = new Acl();
        String[] owners = {"owner1@devint.osdu.com"};
        String[] viewers = {"viewer1@devint.osdu.com"};
        recordAcl.setOwners(owners);
        recordAcl.setViewers(viewers);
        recordMetadata.setAcl(recordAcl);

        return recordMetadata;
    }

    private JsonPatch getJsonPatchFromJsonString(String jsonString) throws IOException {
        final InputStream in = new ByteArrayInputStream(jsonString.getBytes());
        return mapper.readValue(in, JsonPatch.class);
    }

    private String getValidInputJsonForPatch() {
        return "[\n" +
                "    {\n" +
                "        \"op\": \"add\",\n" +
                "        \"path\": \"/tags\",\n" +
                "        \"value\": {\n" +
                "            \"tag3\" : \"value3\"\n" +
                "        }\n" +
                "    }\n" +
                "]";
    }

}
