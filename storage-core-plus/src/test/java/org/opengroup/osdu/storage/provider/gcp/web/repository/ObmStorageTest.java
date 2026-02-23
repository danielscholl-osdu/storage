/*
 *  Copyright @ Microsoft Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.opengroup.osdu.storage.provider.gcp.web.repository;

import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.partition.PartitionPropertyResolver;
import org.opengroup.osdu.core.obm.core.Driver;
import org.opengroup.osdu.core.obm.core.ObmDriverRuntimeException;
import org.opengroup.osdu.core.obm.core.S3CompatibleErrors;
import org.opengroup.osdu.core.obm.core.model.Blob;
import org.opengroup.osdu.core.obm.core.persistence.ObmDestination;
import org.opengroup.osdu.storage.provider.gcp.web.config.PartitionPropertyNames;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.service.DataAuthorizationService;
import org.opengroup.osdu.storage.service.IEntitlementsExtensionService;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.contains;

@ExtendWith(MockitoExtension.class)
@DisplayName("ObmStorage Tests")
class ObmStorageTest {

    @Mock
    private Driver storage;

    @Mock
    private DataAuthorizationService dataAuthorizationService;

    @Mock
    private DpsHeaders headers;

    @Mock
    private TenantInfo tenantInfo;

    @Mock
    @SuppressWarnings("rawtypes")
    private IRecordsMetadataRepository recordRepository;

    @Mock
    private IEntitlementsExtensionService entitlementsService;

    @Mock
    private ExecutorService threadPool;

    @Mock
    private JaxRsDpsLog log;

    @Mock
    private PartitionPropertyResolver partitionPropertyResolver;

    @Mock
    private PartitionPropertyNames partitionPropertyNames;

    @Captor
    private ArgumentCaptor<List<Callable<Boolean>>> tasksCaptor;

    @InjectMocks
    private ObmStorage obmStorage;

    private static final String DATA_PARTITION_ID = "test-partition";
    private static final String PROJECT_ID = "test-project";
    private static final String TENANT_NAME = "test-tenant";
    private static final String BUCKET_NAME = "test-project-test-tenant-records";
    private static final String RECORD_ID = "test-record-id";
    private static final Long VERSION = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(tenantInfo.getDataPartitionId()).thenReturn(DATA_PARTITION_ID);
        lenient().when(tenantInfo.getProjectId()).thenReturn(PROJECT_ID);
        lenient().when(tenantInfo.getName()).thenReturn(TENANT_NAME);
        lenient().when(partitionPropertyResolver.getOptionalPropertyValue(any(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("Write Tests")
    class WriteTests {

        @Test
        @DisplayName("Should successfully write single record")
        void shouldWriteSingleRecord() throws Exception {
            // Arrange
            RecordProcessing recordProcessing = createRecordProcessing();

            when(entitlementsService.isDataManager(headers)).thenReturn(true);
            when(storage.createAndGetBlob(any(Blob.class), any(byte[].class), any(ObmDestination.class)))
                    .thenReturn(mock(Blob.class));

            // Mock threadPool to actually execute the tasks
            when(threadPool.invokeAll(anyList())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<Callable<Boolean>> tasks = invocation.getArgument(0);
                List<Future<Boolean>> futures = new ArrayList<>();
                for (Callable<Boolean> task : tasks) {
                    // Actually execute the task
                    Boolean result = task.call();
                    @SuppressWarnings("unchecked")
                    Future<Boolean> future = mock(Future.class);
                    when(future.get()).thenReturn(result);
                    futures.add(future);
                }
                return futures;
            });

            // Act
            obmStorage.write(recordProcessing);

            // Assert
            verify(threadPool).invokeAll(anyList());
            verify(storage).createAndGetBlob(any(Blob.class), any(byte[].class), any(ObmDestination.class));
        }

        @Test
        @DisplayName("Should successfully write multiple records")
        void shouldWriteMultipleRecords() throws Exception {
            // Arrange
            RecordProcessing record1 = createRecordProcessing();
            RecordProcessing record2 = createRecordProcessing();

            when(entitlementsService.isDataManager(headers)).thenReturn(true);
            when(storage.createAndGetBlob(any(Blob.class), any(byte[].class), any(ObmDestination.class)))
                    .thenReturn(mock(Blob.class));

            // Mock threadPool to actually execute the tasks
            when(threadPool.invokeAll(anyList())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<Callable<Boolean>> tasks = invocation.getArgument(0);
                List<Future<Boolean>> futures = new ArrayList<>();
                for (Callable<Boolean> task : tasks) {
                    // Actually execute the task
                    Boolean result = task.call();
                    @SuppressWarnings("unchecked")
                    Future<Boolean> future = mock(Future.class);
                    when(future.get()).thenReturn(result);
                    futures.add(future);
                }
                return futures;
            });

            // Act
            obmStorage.write(record1, record2);

            // Assert
            verify(threadPool).invokeAll(anyList());
            verify(storage, times(2)).createAndGetBlob(any(Blob.class), any(byte[].class), any(ObmDestination.class));
        }

        @Test
        @DisplayName("Should throw AppException when group validation fails")
        void shouldThrowExceptionWhenGroupValidationFails() {
            // Arrange
            RecordProcessing recordProcessing = createRecordProcessing();
            Groups groups = new Groups();
            groups.setGroups(Collections.emptyList());

            when(entitlementsService.isDataManager(headers)).thenReturn(false);
            when(entitlementsService.getGroups(headers)).thenReturn(groups);

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.write(recordProcessing);
            });

            assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
            assertTrue(exception.getError().getMessage().contains("Could not find group"));
        }

        @Test
        @DisplayName("Should throw AppException when storage driver throws forbidden exception")
        void shouldThrowExceptionWhenStorageDriverThrowsForbidden() throws Exception {
            // Arrange
            RecordProcessing recordProcessing = createRecordProcessing();

            @SuppressWarnings("unchecked")
            Future<Boolean> future = (Future<Boolean>) mock(Future.class);

            when(entitlementsService.isDataManager(headers)).thenReturn(true);

            List<Future<Boolean>> futures = new ArrayList<>();
            futures.add(future);
            doReturn(futures).when(threadPool).invokeAll(anyList());

            when(future.get()).thenThrow(new java.util.concurrent.ExecutionException(
                    new AppException(HttpStatus.SC_FORBIDDEN, "Error on writing record", "Permission denied")));

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.write(recordProcessing);
            });

            assertEquals(HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
        }

        @Test
        @DisplayName("Should handle InterruptedException during write")
        void shouldHandleInterruptedException() throws Exception {
            // Arrange
            RecordProcessing recordProcessing = createRecordProcessing();
            when(entitlementsService.isDataManager(headers)).thenReturn(true);
            when(threadPool.invokeAll(anyList())).thenThrow(new InterruptedException());

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.write(recordProcessing);
            });

            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, exception.getError().getCode());
            assertTrue(Thread.interrupted()); // Clear interrupted status
        }
    }

    @Nested
    @DisplayName("Update Object Metadata Tests")
    class UpdateObjectMetadataTests {

        @Test
        @DisplayName("Should successfully update object metadata")
        @SuppressWarnings("unchecked")
        void shouldUpdateObjectMetadata() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            List<RecordMetadata> recordsMetadata = Collections.singletonList(metadata);
            List<String> recordsId = Collections.singletonList(RECORD_ID);
            List<RecordMetadata> validMetadata = new ArrayList<>();
            List<String> lockedRecords = new ArrayList<>();
            Map<String, String> recordsIdMap = new HashMap<>();
            // Format: id:kind:schema:version (4 parts when split by ":")
            recordsIdMap.put(RECORD_ID, RECORD_ID + ":kind:schema:1");

            Map<String, RecordMetadata> currentRecords = new HashMap<>();
            currentRecords.put(RECORD_ID, metadata);

            when(recordRepository.get(recordsId, Optional.empty())).thenReturn(currentRecords);
            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn(mock(Blob.class));

            // Act
            Map<String, Acl> result = obmStorage.updateObjectMetadata(
                    recordsMetadata, recordsId, validMetadata, lockedRecords, recordsIdMap, Optional.empty());

            // Assert
            assertEquals(1, result.size());
            assertEquals(1, validMetadata.size());
            assertTrue(lockedRecords.isEmpty());
            verify(storage).getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));
        }


    }

    @Nested
    @DisplayName("Revert Object Metadata Tests")
    class RevertObjectMetadataTests {

        @Test
        @DisplayName("Should successfully revert object metadata")
        void shouldRevertObjectMetadata() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            List<RecordMetadata> recordsMetadata = Collections.singletonList(metadata);
            Map<String, Acl> originalAcls = new HashMap<>();

            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn(mock(Blob.class));

            // Act
            obmStorage.revertObjectMetadata(recordsMetadata, originalAcls, Optional.empty());

            // Assert
            verify(storage).getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));
        }
    }

    @Nested
    @DisplayName("Has Access Tests")
    class HasAccessTests {

        @Test
        @DisplayName("Should return true for empty records array")
        void shouldReturnTrueForEmptyArray() {
            // Act
            boolean result = obmStorage.hasAccess();

            // Assert
            assertTrue(result);
            verifyNoInteractions(storage);
        }

        @Test
        @DisplayName("Should return true when access is granted")
        void shouldReturnTrueWhenAccessGranted() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(entitlementsService.isDataManager(headers)).thenReturn(true);
            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn(mock(Blob.class));

            // Act
            boolean result = obmStorage.hasAccess(metadata);

            // Assert
            assertTrue(result);
            verify(storage).getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));
        }

        @Test
        @DisplayName("Should throw AppException when blob not found")
        void shouldThrowExceptionWhenBlobNotFound() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(entitlementsService.isDataManager(headers)).thenReturn(true);
            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn(null);

            // Act & Assert - Production code wraps in AppException
            assertThrows(AppException.class, () -> {
                obmStorage.hasAccess(metadata);
            });
        }

        @Test
        @DisplayName("Should throw AppException when metadata validation fails")
        void shouldThrowExceptionWhenMetadataValidationFails() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            Groups groups = new Groups();
            groups.setGroups(Collections.emptyList());

            when(entitlementsService.isDataManager(headers)).thenReturn(false);
            when(entitlementsService.getGroups(headers)).thenReturn(groups);

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.hasAccess(metadata);
            });

            assertEquals(HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
        }

        @Test
        @DisplayName("Should skip inactive records")
        void shouldSkipInactiveRecords() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            metadata.setStatus(RecordState.deleted);
            when(entitlementsService.isDataManager(headers)).thenReturn(true);

            // Act
            boolean result = obmStorage.hasAccess(metadata);

            // Assert
            assertTrue(result);
            verifyNoInteractions(storage);
        }

        @Test
        @DisplayName("Should handle ObmDriverRuntimeException")
        void shouldHandleObmDriverRuntimeException() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(entitlementsService.isDataManager(headers)).thenReturn(true);
            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenThrow(new ObmDriverRuntimeException(getAccessDeniedError(),
                            new RuntimeException("Access denied")));

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.hasAccess(metadata);
            });

            assertEquals(HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
        }
    }

    @Nested
    @DisplayName("Read Single Record Tests")
    class ReadSingleRecordTests {

        @Test
        @DisplayName("Should successfully read record")
        void shouldReadRecord() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            String expectedContent = "{\"data\":\"test\"}";

            when(dataAuthorizationService.validateViewerOrOwnerAccess(metadata, OperationType.view))
                    .thenReturn(true);
            when(storage.getBlobContent(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn(expectedContent.getBytes(StandardCharsets.UTF_8));

            // Act
            String result = obmStorage.read(metadata, VERSION, false);

            // Assert
            assertEquals(expectedContent, result);
            verify(storage).getBlobContent(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));
        }

        @Test
        @DisplayName("Should throw AppException when access is denied")
        void shouldThrowExceptionWhenAccessDenied() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(dataAuthorizationService.validateViewerOrOwnerAccess(metadata, OperationType.view))
                    .thenReturn(false);

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.read(metadata, VERSION, false);
            });

            assertEquals(HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
            verifyNoInteractions(storage);
        }

        @Test
        @DisplayName("Should throw AppException when record not found")
        void shouldThrowExceptionWhenRecordNotFound() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(dataAuthorizationService.validateViewerOrOwnerAccess(metadata, OperationType.view))
                    .thenReturn(true);
            when(storage.getBlobContent(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenThrow(new ObmDriverRuntimeException(getNotFoundError(),
                            new RuntimeException("Not found")));

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.read(metadata, VERSION, false);
            });

            assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getError().getCode());
        }

        @Test
        @DisplayName("Should throw AppException on storage forbidden error")
        void shouldThrowExceptionOnStorageForbiddenError() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(dataAuthorizationService.validateViewerOrOwnerAccess(metadata, OperationType.view))
                    .thenReturn(true);
            when(storage.getBlobContent(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenThrow(new ObmDriverRuntimeException(getAccessDeniedError(),
                            new RuntimeException("Forbidden")));

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.read(metadata, VERSION, false);
            });

            assertEquals(HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
        }
    }

    @Nested
    @DisplayName("Read Multiple Records Tests")
    class ReadMultipleRecordsTests {

        @Test
        @DisplayName("Should successfully read multiple records")
        void shouldReadMultipleRecords() throws Exception {
            // Arrange
            Map<String, String> objects = new HashMap<>();
            objects.put("key1", "path/to/record1/1");
            objects.put("key2", "path/to/record2/1");

            when(threadPool.invokeAll(anyList())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<Callable<Boolean>> tasks = (List<Callable<Boolean>>) invocation.getArgument(0);
                List<Future<Boolean>> futures = new ArrayList<>();
                for (Callable<Boolean> task : tasks) {
                    try {
                        task.call();
                        @SuppressWarnings("unchecked")
                        Future<Boolean> future = (Future<Boolean>) mock(Future.class);
                        lenient().when(future.get()).thenReturn(true);
                        futures.add(future);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                return futures;
            });

            lenient().when(storage.getBlobContent(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn("{\"data\":\"test\"}".getBytes(StandardCharsets.UTF_8));

            // Act
            Map<String, String> result = obmStorage.read(objects, Optional.empty());

            // Assert
            assertNotNull(result);
            verify(threadPool).invokeAll(anyList());
        }

        @Test
        @DisplayName("Should handle InterruptedException")
        void shouldHandleInterruptedException() throws Exception {
            // Arrange
            Map<String, String> objects = new HashMap<>();
            objects.put("key1", "path/to/record1/1");

            when(threadPool.invokeAll(anyList())).thenThrow(new InterruptedException());

            // Act
            Map<String, String> result = obmStorage.read(objects, Optional.empty());

            // Assert
            assertNotNull(result);
            assertTrue(Thread.interrupted()); // Clear interrupted status
        }
    }

    @Nested
    @DisplayName("Get Hash Tests")
    class GetHashTests {

        @Test
        @DisplayName("Should successfully get hashes for records")
        void shouldGetHashesForRecords() {
            // Arrange
            RecordMetadata metadata1 = createRecordMetadata();
            RecordMetadata metadata2 = createRecordMetadata();
            metadata2.setId("record-2");

            Collection<RecordMetadata> records = Arrays.asList(metadata1, metadata2);

            Blob blob1 = mock(Blob.class);
            Blob blob2 = mock(Blob.class);
            lenient().when(blob1.getChecksum()).thenReturn("hash1");
            lenient().when(blob2.getChecksum()).thenReturn("hash2");

            List<Blob> blobs = Arrays.asList(blob1, blob2);
            lenient().when(storage.listBlobsByName(eq(BUCKET_NAME), any(ObmDestination.class), any(String[].class)))
                    .thenReturn(blobs);

            // Act
            Map<String, String> result = obmStorage.getHash(records);

            // Assert
            assertEquals(2, result.size());
            verify(storage).listBlobsByName(eq(BUCKET_NAME), any(ObmDestination.class), any(String[].class));
        }

        @Test
        @DisplayName("Should return empty hash when blob is null")
        void shouldReturnEmptyHashWhenBlobIsNull() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            Collection<RecordMetadata> records = Collections.singletonList(metadata);

            List<Blob> blobs = Collections.singletonList(null);
            when(storage.listBlobsByName(eq(BUCKET_NAME), any(ObmDestination.class), any(String[].class)))
                    .thenReturn(blobs);

            // Act
            Map<String, String> result = obmStorage.getHash(records);

            // Assert
            assertEquals(1, result.size());
            assertEquals("", result.get(RECORD_ID));
        }
    }

    @Nested
    @DisplayName("Delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should successfully delete record")
        void shouldDeleteRecord() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            metadata.getGcsVersionPaths().add("path/to/version/1");

            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn(mock(Blob.class));
            // deleteBlobs returns void or something - don't mock it, just let it be called

            // Act
            obmStorage.delete(metadata);

            // Assert
            verify(storage).getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));
            verify(storage).deleteBlobs(eq(BUCKET_NAME), any(ObmDestination.class), any(String[].class));
        }

        @Test
        @DisplayName("Should throw AppException when blob not found")
        void shouldThrowExceptionWhenBlobNotFound() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn(null);

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.delete(metadata);
            });

            assertEquals(HttpStatus.SC_NOT_FOUND, exception.getError().getCode());
        }

        @Test
        @DisplayName("Should throw AppException on storage error")
        void shouldThrowExceptionOnStorageError() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenThrow(new ObmDriverRuntimeException(getAccessDeniedError(),
                            new RuntimeException("Access denied")));

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.delete(metadata);
            });

            assertEquals(HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
        }
    }

    @Nested
    @DisplayName("Delete Version Tests")
    class DeleteVersionTests {

        @Test
        @DisplayName("Should successfully delete specific version")
        void shouldDeleteVersion() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn(mock(Blob.class));
            // deleteBlob is not void - don't mock it

            // Act
            obmStorage.deleteVersion(metadata, VERSION);

            // Assert
            verify(storage).getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));
            verify(storage).deleteBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));
        }

        @Test
        @DisplayName("Should log warning when blob not found")
        void shouldLogWarningWhenBlobNotFound() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn(null);

            // Act
            obmStorage.deleteVersion(metadata, VERSION);

            // Assert
            verify(log).warning(contains("does not exist, unable to purge version"));
        }

        @Test
        @DisplayName("Should throw AppException on storage error")
        void shouldThrowExceptionOnStorageError() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenThrow(new ObmDriverRuntimeException(getAccessDeniedError(),
                            new RuntimeException("Access denied")));

            // Act & Assert
            AppException exception = assertThrows(AppException.class, () -> {
                obmStorage.deleteVersion(metadata, VERSION);
            });

            assertEquals(HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
        }
    }

    @Nested
    @DisplayName("Delete Versions Tests")
    class DeleteVersionsTests {

        @Test
        @DisplayName("Should successfully delete multiple versions")
        void shouldDeleteVersions() {
            // Arrange
            List<String> versionPaths = Arrays.asList("path/version/1", "path/version/2");
            // deleteBlob is not void - don't mock it

            // Act
            obmStorage.deleteVersions(versionPaths);

            // Assert
            verify(storage, times(2)).deleteBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));
        }

        @Test
        @DisplayName("Should throw AppException on storage error")
        void shouldThrowExceptionOnStorageError() {
            // Arrange
            List<String> versionPaths = Collections.singletonList("path/version/1");
            doThrow(new ObmDriverRuntimeException(getAccessDeniedError(),
                    new RuntimeException("Access denied")))
                    .when(storage).deleteBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));

            // Act & Assert
            assertThrows(AppException.class, () -> {
                obmStorage.deleteVersions(versionPaths);
            });
        }
    }

    @Nested
    @DisplayName("Is Duplicate Record Tests")
    class IsDuplicateRecordTests {

        @Test
        @DisplayName("Should return true for duplicate record")
        void shouldReturnTrueForDuplicate() {
            // Arrange
            TransferInfo transfer = new TransferInfo();
            transfer.setSkippedRecords(new ArrayList<>());

            RecordMetadata metadata = createRecordMetadata();
            RecordData recordData = new RecordData();
            Map.Entry<RecordMetadata, RecordData> entry =
                    new AbstractMap.SimpleEntry<>(metadata, recordData);

            String existingHash = "existing-hash";
            Map<String, String> hashMap = new HashMap<>();
            hashMap.put(RECORD_ID, existingHash);

            Gson gson = new Gson();
            String recordJson = gson.toJson(recordData);
            byte[] bytes = recordJson.getBytes(StandardCharsets.UTF_8);

            when(storage.getCalculatedChecksum(bytes)).thenReturn(existingHash);

            // Act
            boolean result = obmStorage.isDuplicateRecord(transfer, hashMap, entry);

            // Assert
            assertTrue(result);
            assertEquals(1, transfer.getSkippedRecords().size());
            assertEquals(RECORD_ID, transfer.getSkippedRecords().get(0));
        }

        @Test
        @DisplayName("Should return false for non-duplicate record")
        void shouldReturnFalseForNonDuplicate() {
            // Arrange
            TransferInfo transfer = new TransferInfo();
            transfer.setSkippedRecords(new ArrayList<>());

            RecordMetadata metadata = createRecordMetadata();
            RecordData recordData = new RecordData();
            Map.Entry<RecordMetadata, RecordData> entry =
                    new AbstractMap.SimpleEntry<>(metadata, recordData);

            String existingHash = "existing-hash";
            String newHash = "new-hash";
            Map<String, String> hashMap = new HashMap<>();
            hashMap.put(RECORD_ID, existingHash);

            Gson gson = new Gson();
            String recordJson = gson.toJson(recordData);
            byte[] bytes = recordJson.getBytes(StandardCharsets.UTF_8);

            when(storage.getCalculatedChecksum(bytes)).thenReturn(newHash);

            // Act
            boolean result = obmStorage.isDuplicateRecord(transfer, hashMap, entry);

            // Assert
            assertFalse(result);
            assertTrue(transfer.getSkippedRecords().isEmpty());
        }
    }

    @Nested
    @DisplayName("Bucket Name Tests")
    class BucketNameTests {

        @Test
        @DisplayName("Should use custom bucket name from partition properties")
        void shouldUseCustomBucketName() {
            // Arrange
            String customBucket = "custom-bucket-name";
            when(partitionPropertyNames.getStorageBucketName()).thenReturn("storage.bucket.name");
            when(partitionPropertyResolver.getOptionalPropertyValue("storage.bucket.name", DATA_PARTITION_ID))
                    .thenReturn(Optional.of(customBucket));

            // Re-create obmStorage to pick up new mock behavior
            ObmStorage newObmStorage = new ObmStorage(
                    storage, dataAuthorizationService, headers, tenantInfo, recordRepository,
                    entitlementsService, threadPool, log, partitionPropertyResolver, partitionPropertyNames
            );

            RecordMetadata metadata = createRecordMetadata();
            when(entitlementsService.isDataManager(headers)).thenReturn(true);
            when(storage.getBlob(eq(customBucket), anyString(), any(ObmDestination.class)))
                    .thenReturn(mock(Blob.class));

            // Act
            newObmStorage.hasAccess(metadata);

            // Assert
            verify(storage).getBlob(eq(customBucket), anyString(), any(ObmDestination.class));
        }

        @Test
        @DisplayName("Should use default bucket name when partition property not set")
        void shouldUseDefaultBucketName() {
            // Arrange
            RecordMetadata metadata = createRecordMetadata();
            when(entitlementsService.isDataManager(headers)).thenReturn(true);
            when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                    .thenReturn(mock(Blob.class));

            // Act
            obmStorage.hasAccess(metadata);

            // Assert
            verify(storage).getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));
        }
    }

    // Helper methods
    private RecordProcessing createRecordProcessing() {
        RecordProcessing processing = new RecordProcessing();
        processing.setRecordMetadata(createRecordMetadata());
        processing.setRecordData(new RecordData());
        return processing;
    }

    private RecordMetadata createRecordMetadata() {
        RecordMetadata metadata = new RecordMetadata();
        metadata.setId(RECORD_ID);
        metadata.setStatus(RecordState.active);

        Acl acl = new Acl();
        acl.setViewers(new String[]{"group1@test.com"});
        acl.setOwners(new String[]{"group2@test.com"});
        metadata.setAcl(acl);

        // Add version paths - required for getVersionPath() to work
        List<String> versionPaths = new ArrayList<>();
        versionPaths.add(RECORD_ID + "/" + VERSION);
        metadata.setGcsVersionPaths(versionPaths);

        return metadata;
    }

    /**
     * Returns the error code for 403/Forbidden errors.
     * Using string literal since S3CompatibleErrors doesn't have a 403 constant.
     */
    private String getAccessDeniedError() {
        return "AccessDenied";  // Standard S3 error code for 403
    }

    /**
     * Returns the S3CompatibleErrors constant name for 404/Not Found errors.
     */
    private String getNotFoundError() {
        return S3CompatibleErrors.NO_SUCH_KEY_CODE;
    }

    // Parameterized test examples
    @ParameterizedTest
    @MethodSource("provideRecordStates")
    @DisplayName("Should handle different record states")
    void shouldHandleDifferentRecordStates(RecordState state, boolean shouldProcess) {
        // Arrange
        RecordMetadata metadata = createRecordMetadata();
        metadata.setStatus(state);
        lenient().when(entitlementsService.isDataManager(headers)).thenReturn(true);
        lenient().when(storage.getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                .thenReturn(mock(Blob.class));

        // Act
        boolean result = obmStorage.hasAccess(metadata);

        // Assert
        assertTrue(result);
        if (shouldProcess) {
            verify(storage).getBlob(eq(BUCKET_NAME), anyString(), any(ObmDestination.class));
        } else {
            verifyNoInteractions(storage);
        }
    }

    private static Stream<Arguments> provideRecordStates() {
        return Stream.of(
                Arguments.of(RecordState.active, true),
                Arguments.of(RecordState.deleted, false),
                Arguments.of(RecordState.purged, false)
        );
    }

    @ParameterizedTest
    @MethodSource("provideHttpStatusCodes")
    @DisplayName("Should handle different HTTP error codes from storage driver")
    void shouldHandleDifferentHttpErrorCodes(int httpStatus, int expectedStatus) {
        // Arrange
        RecordMetadata metadata = createRecordMetadata();
        when(dataAuthorizationService.validateViewerOrOwnerAccess(metadata, OperationType.view))
                .thenReturn(true);

        String error = httpStatus == HttpStatus.SC_FORBIDDEN ?
                getAccessDeniedError() : getNotFoundError();

        when(storage.getBlobContent(eq(BUCKET_NAME), anyString(), any(ObmDestination.class)))
                .thenThrow(new ObmDriverRuntimeException(error, new RuntimeException("Error")));

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            obmStorage.read(metadata, VERSION, false);
        });

        assertEquals(expectedStatus, exception.getError().getCode());
    }

    private static Stream<Arguments> provideHttpStatusCodes() {
        return Stream.of(
                Arguments.of(HttpStatus.SC_FORBIDDEN, HttpStatus.SC_FORBIDDEN),
                Arguments.of(HttpStatus.SC_NOT_FOUND, HttpStatus.SC_UNPROCESSABLE_ENTITY)
        );
    }
}
