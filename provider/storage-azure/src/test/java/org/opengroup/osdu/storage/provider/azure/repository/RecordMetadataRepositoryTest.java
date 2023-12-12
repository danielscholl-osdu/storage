package org.opengroup.osdu.storage.provider.azure.repository;

import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.azure.cosmosdb.CosmosStore;
import org.opengroup.osdu.azure.cosmosdb.CosmosStoreBulkOperations;
import org.opengroup.osdu.azure.query.CosmosStorePageRequest;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.azure.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.azure.model.DocumentCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static java.util.Collections.singletonList;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordMetadataRepositoryTest {
    private final static String RECORD_ID1 = "opendes:id1:15706318658560";
    private final static String RECORD_ID2 = "opendes:id2:15706318658560";
    private final static String KIND = "opendes:source:type:1.0.0";
    private final static String STATUS = "active";
    private final ObjectMapper mapper = new ObjectMapper();
    @Rule
    ExpectedException exceptionRule = ExpectedException.none();
    @Mock
    private JaxRsDpsLog logger;
    @Mock
    private CosmosStoreBulkOperations cosmosBulkStore;
    @Mock
    private DpsHeaders headers;
    @Mock
    private Page<RecordMetadataDoc> page;
    @Mock
    private CosmosStore cosmosStore;
    @InjectMocks
    private RecordMetadataRepository recordMetadataRepository;

    @BeforeEach
    void setup() {
        lenient().when(headers.getPartitionId()).thenReturn("opendes");
        ReflectionTestUtils.setField(recordMetadataRepository, "cosmosDBName", "osdu-db");
        ReflectionTestUtils.setField(recordMetadataRepository, "recordMetadataCollection", "collection");
        ReflectionTestUtils.setField(recordMetadataRepository, "minBatchSizeToUseBulkUpload", 2);
    }


    @Test
    void shouldFailOnCreateOrUpdate_IfAclIsNull() {
        try {
            recordMetadataRepository.createOrUpdate(singletonList(new RecordMetadata()), Optional.empty());
        } catch (IllegalArgumentException e) {
            assertEquals("Acl of the record must not be null", e.getMessage());
        }

    }

    @Test
    void shouldSetCorrectDocId_IfCollaborationContextIsProvided_InParallel() {
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
        assertEquals(capturedDoc1.getId(), CollaborationId + RECORD_ID1);
        RecordMetadataDoc capturedDoc2 = (RecordMetadataDoc) capturedDocs.get(1);
        assertEquals(capturedDoc2.getId(), CollaborationId + RECORD_ID2);
    }

    @Test
    void shouldSetCorrectDocId_IfCollaborationContextIsProvided_InSerial() {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).build();

        String expectedDocId = CollaborationId + RECORD_ID1;
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);
        recordMetadataRepository.createOrUpdate(singletonList(recordMetadata), Optional.of(collaborationContext));

        ArgumentCaptor<RecordMetadataDoc> itemCaptor = ArgumentCaptor.forClass(RecordMetadataDoc.class);
        verify(cosmosStore).upsertItem(any(),
                any(),
                eq("collection"),
                eq(CollaborationId + RECORD_ID1),
                itemCaptor.capture());

        RecordMetadataDoc capturedItem = itemCaptor.getValue();
        System.out.println("jh");
        assertEquals(expectedDocId, capturedItem.getId());
    }

    @Test
    void shouldPatchRecordsWithCorrectDocId_whenCollaborationContextIsProvided() throws IOException {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).build();
        String expectedDocId = CollaborationId + RECORD_ID1;
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        jsonPatchPerRecord.put(recordMetadata, getJsonPatchFromJsonString(getValidInputJsonForPatch()));
        Map<String, String> partitionKeyForDoc = new HashMap<>();
        partitionKeyForDoc.put(expectedDocId, RECORD_ID1);
        Map<String, String> recordErrors = recordMetadataRepository.patch(jsonPatchPerRecord, Optional.of(collaborationContext));
        verify(cosmosBulkStore, times(1)).bulkPatchWithCosmosClient(eq("opendes"), eq("osdu-db"), eq("collection"), anyMap(), eq(partitionKeyForDoc), eq(1));
        assertTrue((recordErrors.isEmpty()));
    }

    @Test
    void shouldPatchRecordsWithCorrectDocId_whenCollaborationContextIsNotProvided() throws IOException {
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        jsonPatchPerRecord.put(recordMetadata, getJsonPatchFromJsonString(getValidInputJsonForPatch()));
        Map<String, String> partitionKeyForDoc = new HashMap<>();
        partitionKeyForDoc.put(RECORD_ID1, RECORD_ID1);
        Map<String, String> recordErrors = recordMetadataRepository.patch(jsonPatchPerRecord, Optional.empty());
        verify(cosmosBulkStore, times(1)).bulkPatchWithCosmosClient(eq("opendes"), eq("osdu-db"), eq("collection"), anyMap(), eq(partitionKeyForDoc), eq(1));
        assertTrue((recordErrors.isEmpty()));
    }

    @Test
    void shouldPatchRecordsWithCorrectDocId_whenCollaborationContextIsNotProvided_withDuplicateOpAndPath() throws IOException {
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        jsonPatchPerRecord.put(recordMetadata, getJsonPatchFromJsonString(getValidInputJsonForPatchWithSameOpAndPath()));
        Map<String, String> partitionKeyForDoc = new HashMap<>();
        partitionKeyForDoc.put(RECORD_ID1, RECORD_ID1);
        Map<String, String> recordErrors = recordMetadataRepository.patch(jsonPatchPerRecord, Optional.empty());
        verify(cosmosBulkStore, times(1)).bulkPatchWithCosmosClient(eq("opendes"), eq("osdu-db"), eq("collection"), anyMap(), eq(partitionKeyForDoc), eq(1));
        assertTrue((recordErrors.isEmpty()));
    }

    @Test
    void shouldReturnErrors_whenPatchFailsWithAppExceptionWithoutCollaborationContext() throws IOException {
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        jsonPatchPerRecord.put(recordMetadata, getJsonPatchFromJsonString(getValidInputJsonForPatch()));
        Map<String, String> partitionKeyForDoc = new HashMap<>();
        partitionKeyForDoc.put(RECORD_ID1, RECORD_ID1);

        AppException appException = mock(AppException.class);
        AppException originalException = mock(AppException.class);
        AppError appError = mock(AppError.class);
        String[] errors = new String[2];
        errors[0] = "recordId:123|unknown error with status 500|unknown exception: reason is|also-unknown";
        errors[1] = "recordId456|cosmos error with status 400";
        when(appError.getErrors()).thenReturn(errors);
        when(originalException.getError()).thenReturn(appError);
        when(appException.getOriginalException()).thenReturn(originalException);

        doThrow(appException).when(cosmosBulkStore).bulkPatchWithCosmosClient(eq("opendes"), eq("osdu-db"), eq("collection"), anyMap(), eq(partitionKeyForDoc), eq(1));
        Map<String, String> patchErrors = recordMetadataRepository.patch(jsonPatchPerRecord, Optional.empty());
        assertEquals(2, patchErrors.size());
        assertEquals("unknown error with status 500", patchErrors.get("recordId:123"));
        assertEquals("cosmos error with status 400", patchErrors.get("recordId456"));
    }

    @Test
    void shouldReturnErrors_whenPatchFailsWithAppExceptionWithCollaborationContext() throws IOException {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).build();
        String expectedDocId = CollaborationId + RECORD_ID1;
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        jsonPatchPerRecord.put(recordMetadata, getJsonPatchFromJsonString(getValidInputJsonForPatch()));
        Map<String, String> partitionKeyForDoc = new HashMap<>();
        partitionKeyForDoc.put(expectedDocId, RECORD_ID1);

        AppException appException = mock(AppException.class);
        AppException originalException = mock(AppException.class);
        AppError appError = mock(AppError.class);
        String[] errors = new String[2];
        errors[0] = CollaborationId + "recordId:123|unknown error with status 500|unknown exception";
        errors[1] = CollaborationId + "recordId456|cosmos error with status 400";
        when(appError.getErrors()).thenReturn(errors);
        when(originalException.getError()).thenReturn(appError);
        when(appException.getOriginalException()).thenReturn(originalException);

        doThrow(appException).when(cosmosBulkStore).bulkPatchWithCosmosClient(eq("opendes"), eq("osdu-db"), eq("collection"), anyMap(), eq(partitionKeyForDoc), eq(1));
        Map<String, String> patchErrors = recordMetadataRepository.patch(jsonPatchPerRecord, Optional.of(collaborationContext));
        assertEquals(2, patchErrors.size());
        assertEquals("unknown error with status 500", patchErrors.get("recordId:123"));
        assertEquals("cosmos error with status 400", patchErrors.get("recordId456"));
    }

    @Test
    void shouldThrowException_whenPatchFailsWithOtherException() throws IOException {
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        jsonPatchPerRecord.put(recordMetadata, getJsonPatchFromJsonString(getValidInputJsonForPatch()));
        Map<String, String> partitionKeyForDoc = new HashMap<>();
        partitionKeyForDoc.put(RECORD_ID1, RECORD_ID1);

        AppException appException = mock(AppException.class);
        doThrow(appException).when(cosmosBulkStore).bulkPatchWithCosmosClient(eq("opendes"), eq("osdu-db"), eq("collection"), anyMap(), eq(partitionKeyForDoc), eq(1));
        Optional<CollaborationContext> context = Optional.empty();
        try {
            recordMetadataRepository.patch(jsonPatchPerRecord, context);
            fail("expected exception");
        } catch (AppException e) {

        }
    }

    @Test
    void shouldQueryByDocIdWithCollaborationId_IfCollaborationContextIsProvided() {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).build();
        String expectedQuery = "SELECT c.metadata.id FROM c WHERE c.metadata.kind = '" + KIND + "' AND c.metadata.status = 'active' and STARTSWITH(c.id, '" + CollaborationId + "') ";

        Pageable pageable = PageRequest.of(0, 8);

        doReturn(page).when(cosmosStore).queryItemsPage(eq("opendes"), eq("osdu-db"), eq("collection"), any(SqlQuerySpec.class), any(Class.class), eq(8), any(), any(CosmosQueryRequestOptions.class));

        this.recordMetadataRepository.findIdsByMetadata_kindAndMetadata_status(KIND, STATUS, pageable, Optional.of(collaborationContext));

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        verify(cosmosStore).queryItemsPage(eq("opendes"),
                eq("osdu-db"),
                eq("collection"),
                queryCaptor.capture(),
                any(Class.class),
                eq(8),
                any(),
                any(CosmosQueryRequestOptions.class));
        SqlQuerySpec capturedQuery = queryCaptor.getValue();
        assertEquals(expectedQuery, capturedQuery.getQueryText());
    }

    @Test
    void findIdsByMetadata_kindAndMetadata_status_shouldQueryByDocIdWithCollaborationId_IfCollaborationContextIsProvided() {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).build();

        String expectedQuery = "SELECT c.metadata.id FROM c WHERE c.metadata.kind = '" + KIND + "' AND c.metadata.status = 'active' and STARTSWITH(c.id, '" + CollaborationId + "')";

        List<RecordMetadataDoc> returnList = new ArrayList<>();
        returnList.add(Mockito.mock(RecordMetadataDoc.class));

        doReturn(returnList).when(cosmosStore).queryItems(eq("opendes"),
                eq("osdu-db"),
                eq("collection"),
                any(SqlQuerySpec.class),
                any(CosmosQueryRequestOptions.class),
                eq(RecordMetadataDoc.class));

        this.recordMetadataRepository.findIdsByMetadata_kindAndMetadata_status(KIND, STATUS, Optional.of(collaborationContext));

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        verify(cosmosStore).queryItems(eq("opendes"),
                eq("osdu-db"),
                eq("collection"),
                queryCaptor.capture(),
                any(CosmosQueryRequestOptions.class),
                eq(RecordMetadataDoc.class));
        SqlQuerySpec capturedQuery = queryCaptor.getValue();
        assertEquals(expectedQuery, capturedQuery.getQueryText());
    }

    @Test
    void shouldQueryByDocIdWithCollaborationId_IfCollaborationContextIsNotProvided() {
        String expectedQuery = "SELECT c.metadata.id FROM c WHERE c.metadata.kind = 'opendes:source:type:1.0.0' AND c.metadata.status = 'active' AND c.id = c.metadata.id ";

        Pageable pageable = PageRequest.of(0, 8);

        doReturn(page).when(cosmosStore).queryItemsPage(eq("opendes"), eq("osdu-db"), eq("collection"), any(SqlQuerySpec.class), any(Class.class), eq(8), any(), any(CosmosQueryRequestOptions.class));

        this.recordMetadataRepository.findIdsByMetadata_kindAndMetadata_status(KIND, STATUS, pageable, Optional.empty());

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        verify(cosmosStore).queryItemsPage(eq("opendes"),
                eq("osdu-db"),
                eq("collection"),
                queryCaptor.capture(),
                any(Class.class),
                eq(8),
                any(),
                any(CosmosQueryRequestOptions.class));

        SqlQuerySpec capturedQuery = queryCaptor.getValue();
        assertEquals(expectedQuery, capturedQuery.getQueryText());
    }

    @Test
    void shouldPatchRecordsWithRemovePatch_whenCollaborationContextIsNotProvided_withDuplicateOpAndPath() throws IOException {
        RecordMetadata recordMetadata = createRecord(RECORD_ID1);

        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        jsonPatchPerRecord.put(recordMetadata, getJsonPatchFromJsonString(getValidInputJsonForPatchRemove()));

        Map<String, String> partitionKeyForDoc = new HashMap<>();
        partitionKeyForDoc.put(RECORD_ID1, RECORD_ID1);

        Map<String, String> recordErrors = recordMetadataRepository.patch(jsonPatchPerRecord, Optional.empty());


        verify(cosmosBulkStore, times(1)).bulkPatchWithCosmosClient(eq("opendes"), eq("osdu-db"), eq("collection"), anyMap(), eq(partitionKeyForDoc), eq(1));
        assertTrue((recordErrors.isEmpty()));
    }

    @Test
    void shouldReturnCorrectRecords_when_queryByLegalTagName() {
        String legalTagName = "legal_tag_name";
        int limit = 200;
        String cursor = "cursor";

        String expectedQueryWithTrailingSpace = "SELECT * FROM c WHERE ARRAY_CONTAINS(c.metadata.legal.legaltags, '" + legalTagName + "') ";

        doReturn(page).when(cosmosStore).queryItemsPage(eq("opendes"), eq("osdu-db"), eq("collection"), any(SqlQuerySpec.class), any(Class.class), eq(limit), any(), any(CosmosQueryRequestOptions.class));

        CosmosStorePageRequest pageable = Mockito.mock(CosmosStorePageRequest.class);
        doReturn(pageable).when(page).getPageable();
        doReturn("continuation").when(pageable).getRequestContinuation();

        recordMetadataRepository.queryByLegalTagName(legalTagName, limit, cursor);

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        verify(cosmosStore).queryItemsPage(eq("opendes"),
                eq("osdu-db"),
                eq("collection"),
                queryCaptor.capture(),
                any(Class.class),
                eq(limit),
                any(),
                any(CosmosQueryRequestOptions.class));
        SqlQuerySpec capturedQuery = queryCaptor.getValue();
        assertEquals(expectedQueryWithTrailingSpace, capturedQuery.getQueryText());
    }

    @Test
    void queryByLegalTagName_shouldThrowAppException_when_cosmosStore_throwsInvalidCursorException() {
        String legalTagName = "legal_tag_name";
        int limit = 200;
        String cursor = "invalid%cursor";

        CosmosException cosmosException = mock(CosmosException.class);
        doReturn(HttpStatus.SC_BAD_REQUEST).when(cosmosException).getStatusCode();
        doReturn("INVALID JSON in continuation token").when(cosmosException).getMessage();

        doThrow(cosmosException).when(cosmosStore).queryItemsPage(eq("opendes"), eq("osdu-db"), eq("collection"), any(SqlQuerySpec.class), any(Class.class), eq(limit), any(), any(CosmosQueryRequestOptions.class));

        AppException appException = assertThrows(AppException.class, () -> recordMetadataRepository.queryByLegalTagName(
                legalTagName, limit, cursor));

        assertEquals(HttpStatus.SC_BAD_REQUEST, appException.getError().getCode());
        assertEquals("Cursor invalid", appException.getError().getReason());
    }

    @Test
    void queryByLegalTagName_shouldThrowCosmosException_when_cosmosStore_throwsGenericCosmosException() {
        String legalTagName = "legal_tag_name";
        int limit = 200;
        String cursor = "invalid%cursor";

        CosmosException cosmosException = mock(CosmosException.class);
        doReturn(HttpStatus.SC_BAD_REQUEST).when(cosmosException).getStatusCode();

        String badRequestMessage = "Some other bad request exception";
        doReturn(badRequestMessage).when(cosmosException).getMessage();

        doThrow(cosmosException).when(cosmosStore).queryItemsPage(eq("opendes"), eq("osdu-db"), eq("collection"), any(SqlQuerySpec.class), any(Class.class), eq(limit), any(), any(CosmosQueryRequestOptions.class));

        CosmosException actualCosmosException = assertThrows(CosmosException.class, () -> recordMetadataRepository.queryByLegalTagName(
                legalTagName, limit, cursor));

        assertEquals(HttpStatus.SC_BAD_REQUEST, actualCosmosException.getStatusCode());
        assertEquals(badRequestMessage, actualCosmosException.getMessage());
    }

    @Test
    void queryByLegalTagName_shouldThrowException_when_cosmosStore_throwsGenericException() {
        String legalTagName = "legal_tag_name";
        int limit = 200;
        String cursor = "invalid%cursor";

        doThrow(RuntimeException.class).when(cosmosStore).queryItemsPage(eq("opendes"), eq("osdu-db"), eq("collection"), any(SqlQuerySpec.class), any(Class.class), eq(limit), any(), any(CosmosQueryRequestOptions.class));

        assertThrows(RuntimeException.class, () -> recordMetadataRepository.queryByLegalTagName(
                legalTagName, limit, cursor));
    }

    @Test
    void getById_shouldReturnRecordMetadata_when_called() {
        RecordMetadataDoc doc = Mockito.mock(RecordMetadataDoc.class);

        doReturn(Optional.of(doc)).when(cosmosStore).findItem("opendes", "osdu-db", "collection", "id", "id", RecordMetadataDoc.class);

        recordMetadataRepository.get("id", Optional.empty());

        verify(cosmosStore).findItem("opendes", "osdu-db", "collection", "id", "id", RecordMetadataDoc.class);
    }

    @Test
    void getById_shouldReturnNull_when_blobStoreFindItemReturnsNull() {
        doReturn(Optional.empty()).when(cosmosStore).findItem("opendes", "osdu-db", "collection", "id", "id", RecordMetadataDoc.class);

        recordMetadataRepository.get("id", Optional.empty());

        verify(cosmosStore).findItem("opendes", "osdu-db", "collection", "id", "id", RecordMetadataDoc.class);
    }

    @Test
    void getByList_shouldReturnValidResultSet_whenCosmosStoreReturnsValidRecords() {
        RecordMetadataDoc doc1 = new RecordMetadataDoc(RECORD_ID1, createRecord(RECORD_ID1));
        RecordMetadataDoc doc2 = new RecordMetadataDoc(RECORD_ID2, createRecord(RECORD_ID2));

        doReturn(Arrays.asList(doc1, doc2)).when(cosmosStore).queryItems(eq("opendes"), eq("osdu-db"), eq("collection"), any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class), eq(RecordMetadataDoc.class));

        String expectedQuery = String.format("SELECT * FROM c WHERE c.id IN (\"%s\",\"%s\")", RECORD_ID1, RECORD_ID2);

        Map<String, RecordMetadata> resultSet = recordMetadataRepository.get(Arrays.asList(RECORD_ID1, RECORD_ID2), Optional.empty());

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);

        verify(cosmosStore).queryItems(eq("opendes"), eq("osdu-db"), eq("collection"), queryCaptor.capture(), any(CosmosQueryRequestOptions.class), eq(RecordMetadataDoc.class));

        SqlQuerySpec capturedQuery = queryCaptor.getValue();
        assertEquals(expectedQuery, capturedQuery.getQueryText());
        assertEquals(2, resultSet.size());
    }

    @Test
    void getByList_shouldReturnEmptyResultSet_whenCosmosStoreReturnsEmptyRecords() {
        RecordMetadataDoc doc1 = mock(RecordMetadataDoc.class);
        RecordMetadataDoc doc2 = mock(RecordMetadataDoc.class);

        doReturn(Arrays.asList(doc1, doc2)).when(cosmosStore).queryItems(eq("opendes"), eq("osdu-db"), eq("collection"), any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class), eq(RecordMetadataDoc.class));

        String expectedQuery = String.format("SELECT * FROM c WHERE c.id IN (\"%s\",\"%s\")", RECORD_ID1, RECORD_ID2);

        Map<String, RecordMetadata> resultSet = recordMetadataRepository.get(Arrays.asList(RECORD_ID1, RECORD_ID2), Optional.empty());

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);

        verify(cosmosStore).queryItems(eq("opendes"), eq("osdu-db"), eq("collection"), queryCaptor.capture(), any(CosmosQueryRequestOptions.class), eq(RecordMetadataDoc.class));

        SqlQuerySpec capturedQuery = queryCaptor.getValue();
        assertEquals(expectedQuery, capturedQuery.getQueryText());
        assertEquals(0, resultSet.size());
    }

    @Test
    void getMetadataDocumentCountForBlob_shouldReturnZero_whenEmptyResultSetReturnedFromCosmos() {
        DocumentCount documentCount = Mockito.mock(DocumentCount.class);
        doReturn(Collections.singletonList(documentCount)).when(cosmosStore).queryItems(eq("opendes"), eq("osdu-db"), eq("collection"), any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class), eq(DocumentCount.class));

        int actualRecordSize = recordMetadataRepository.getMetadataDocumentCountForBlob("path");

        String requiredQuery = "SELECT COUNT(1) AS documentCount from c WHERE ARRAY_CONTAINS (c.metadata.gcsVersionPaths, 'path')";
        ArgumentCaptor<SqlQuerySpec> argumentCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);

        verify(cosmosStore).queryItems(eq("opendes"), eq("osdu-db"), eq("collection"), argumentCaptor.capture(), any(CosmosQueryRequestOptions.class), eq(DocumentCount.class));

        SqlQuerySpec capturedQuery = argumentCaptor.getValue();
        assertEquals(requiredQuery, capturedQuery.getQueryText());
        assertEquals(0, actualRecordSize);
    }

    @Test
    void getMetadataDocumentCountForBlob_shouldReturnValidRecordSize_whenValidResultSetReturnedFromCosmos() {
        DocumentCount documentCount = new DocumentCount(1);
        doReturn(Collections.singletonList(documentCount)).when(cosmosStore).queryItems(eq("opendes"), eq("osdu-db"), eq("collection"), any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class), eq(DocumentCount.class));

        int actualRecordSize = recordMetadataRepository.getMetadataDocumentCountForBlob("path");

        String requiredQuery = "SELECT COUNT(1) AS documentCount from c WHERE ARRAY_CONTAINS (c.metadata.gcsVersionPaths, 'path')";

        ArgumentCaptor<SqlQuerySpec> argumentCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);

        verify(cosmosStore).queryItems(eq("opendes"), eq("osdu-db"), eq("collection"), argumentCaptor.capture(), any(CosmosQueryRequestOptions.class), eq(DocumentCount.class));

        SqlQuerySpec capturedQuery = argumentCaptor.getValue();
        assertEquals(requiredQuery, capturedQuery.getQueryText());
        assertEquals(1, actualRecordSize);
    }

    @Test
    void deleteShould_deleteItemFromCosmos_whenIdIsNotNull() {
        recordMetadataRepository.delete(RECORD_ID1, Optional.empty());

        verify(cosmosStore, times(1)).deleteItem("opendes", "osdu-db", "collection", RECORD_ID1, RECORD_ID1);
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
        recordMetadata.setModifyUser("user1");
        recordMetadata.setModifyTime(123L);
        return recordMetadata;
    }

    private JsonPatch getJsonPatchFromJsonString(String jsonString) throws IOException {
        final InputStream in = new ByteArrayInputStream(jsonString.getBytes());
        return mapper.readValue(in, JsonPatch.class);
    }

    private String getValidInputJsonForPatch() {
        return "[\n" +
                "    {\n" +
                "        \"op\": \"replace\",\n" +
                "        \"path\": \"/kind\",\n" +
                "        \"value\": \"/newKind\"\n" +
                "    }\n" +
                "]";
    }

    private String getValidInputJsonForPatchWithSameOpAndPath() {
        return "[\n" +
                "        {\n" +
                "            \"op\": \"add\",\n" +
                "            \"path\": \"/acl/viewers/1\",\n" +
                "            \"value\": \"viewerAcl1\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"op\": \"add\",\n" +
                "            \"path\": \"/acl/viewers/1\",\n" +
                "            \"value\": \"viewerAcl2\"\n" +
                "        }\n" +
                "    ]";
    }

    private String getValidInputJsonForPatchRemove() {
        return "[\n" +
                "    {\n" +
                "        \"op\": \"remove\",\n" +
                "        \"path\": \"/kind\",\n" +
                "        \"value\": \"/newKind\"\n" +
                "    }\n" +
                "]";
    }

}
