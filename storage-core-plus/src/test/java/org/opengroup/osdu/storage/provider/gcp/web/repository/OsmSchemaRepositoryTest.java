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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.isNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.model.query.GetQuery;
import org.opengroup.osdu.core.osm.core.service.Context;
import org.opengroup.osdu.core.osm.core.service.Transaction;

@ExtendWith(MockitoExtension.class)
@DisplayName("OsmSchemaRepository Tests")
class OsmSchemaRepositoryTest {

    private static final String TEST_KIND = "test-kind";
    private static final String TEST_USER = "test-user";
    private static final String TEST_PARTITION_ID = "test-partition";
    private static final String TEST_TENANT_NAME = "test-tenant";

    @Mock
    private Context context;

    @Mock
    private TenantInfo tenantInfo;

    @Mock
    private Transaction transaction;

    @InjectMocks
    private OsmSchemaRepository repository;

    @Captor
    private ArgumentCaptor<GetQuery<Schema>> queryCaptor;

    @Captor
    private ArgumentCaptor<Destination> destinationCaptor;

    @Captor
    private ArgumentCaptor<Schema> schemaCaptor;

    @Captor
    private ArgumentCaptor<String> kindCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(tenantInfo.getDataPartitionId()).thenReturn(TEST_PARTITION_ID);
        lenient().when(tenantInfo.getName()).thenReturn(TEST_TENANT_NAME);
    }

    // ========================================
    // add() Tests
    // ========================================

    @Nested
    @DisplayName("add() Method Tests")
    class AddTests {

        @Test
        @DisplayName("Should successfully add schema when it doesn't exist")
        void add_SchemaDoesNotExist_CreatesAndCommits() {
            // Arrange
            Schema schema = createSchema(TEST_KIND);
            when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
            when(context.findOne(any(GetQuery.class))).thenReturn(Optional.empty());
            doNothing().when(context).create(any(Destination.class), any(Schema.class));
            doNothing().when(transaction).commitIfActive();
            doNothing().when(transaction).rollbackIfActive();

            // Act
            repository.add(schema, TEST_USER);

            // Assert
            verify(context).beginTransaction(any(Destination.class));
            verify(context).findOne(queryCaptor.capture());
            verify(context).create(any(Destination.class), schemaCaptor.capture());
            verify(transaction).commitIfActive();
            verify(transaction).rollbackIfActive(); // Called in finally block

            // Verify captured schema
            Schema capturedSchema = schemaCaptor.getValue();
            assertEquals(TEST_KIND, capturedSchema.getKind());
        }

        @Test
        @DisplayName("Should throw exception and rollback when schema already exists")
        void add_SchemaExists_ThrowsExceptionAndRollsBack() {
            // Arrange
            Schema schema = createSchema(TEST_KIND);
            Schema existingSchema = createSchema(TEST_KIND);

            when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
            when(context.findOne(any(GetQuery.class))).thenReturn(Optional.of(existingSchema));
            doNothing().when(transaction).rollbackIfActive();

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> repository.add(schema, TEST_USER)
            );

            assertEquals("A schema for the specified kind has already been registered.", exception.getMessage());

            verify(context).beginTransaction(any(Destination.class));
            verify(context).findOne(any(GetQuery.class));
            verify(context, never()).create(any(Destination.class), any(Schema.class));
            verify(transaction, never()).commitIfActive();
            verify(transaction, times(2)).rollbackIfActive(); // Called in if block and finally
        }

        @Test
        @DisplayName("Should rollback in finally block when create throws exception")
        void add_CreateThrowsException_RollsBackInFinally() {
            // Arrange
            Schema schema = createSchema(TEST_KIND);
            when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
            when(context.findOne(any(GetQuery.class))).thenReturn(Optional.empty());

            RuntimeException expectedException = new RuntimeException("Create failed");
            doThrow(expectedException).when(context).create(any(Destination.class), any(Schema.class));
            doNothing().when(transaction).rollbackIfActive();

            // Act & Assert
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> repository.add(schema, TEST_USER));

            assertEquals(expectedException, thrown);
            verify(context).beginTransaction(any(Destination.class));
            verify(context).findOne(any(GetQuery.class));
            verify(context).create(any(Destination.class), any(Schema.class));
            verify(transaction, never()).commitIfActive();
            verify(transaction).rollbackIfActive(); // Called in finally
        }

        @Test
        @DisplayName("Should rollback in finally block when commit throws exception")
        void add_CommitThrowsException_RollsBackInFinally() {
            // Arrange
            Schema schema = createSchema(TEST_KIND);
            when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
            when(context.findOne(any(GetQuery.class))).thenReturn(Optional.empty());
            doNothing().when(context).create(any(Destination.class), any(Schema.class));

            RuntimeException expectedException = new RuntimeException("Commit failed");
            doThrow(expectedException).when(transaction).commitIfActive();
            doNothing().when(transaction).rollbackIfActive();

            // Act & Assert
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> repository.add(schema, TEST_USER));

            assertEquals(expectedException, thrown);
            verify(transaction).commitIfActive();
            verify(transaction).rollbackIfActive(); // Called in finally
        }

        @Test
        @DisplayName("Should throw NullPointerException when transaction is null")
        void add_NullTransaction_ThrowsNullPointerException() {
            // Arrange
            Schema schema = createSchema(TEST_KIND);
            when(context.beginTransaction(any(Destination.class))).thenReturn(null);
            when(context.findOne(any(GetQuery.class))).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NullPointerException.class, () -> repository.add(schema, TEST_USER));

            verify(context).beginTransaction(any(Destination.class));
            verify(context).findOne(any(GetQuery.class));
        }

        @Test
        @DisplayName("Should construct query with correct kind")
        void add_ConstructsQueryWithCorrectKind() {
            // Arrange
            Schema schema = createSchema(TEST_KIND);
            when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
            when(context.findOne(any(GetQuery.class))).thenReturn(Optional.empty());
            doNothing().when(context).create(any(Destination.class), any(Schema.class));
            doNothing().when(transaction).commitIfActive();
            doNothing().when(transaction).rollbackIfActive();

            // Act
            repository.add(schema, TEST_USER);

            // Assert
            verify(context).findOne(queryCaptor.capture());
            GetQuery<Schema> capturedQuery = queryCaptor.getValue();
            assertNotNull(capturedQuery);
            assertNotNull(capturedQuery.getWhere());
        }

        @Test
        @DisplayName("Should use correct destination when adding schema")
        void add_UsesCorrectDestination() {
            // Arrange
            Schema schema = createSchema(TEST_KIND);
            when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
            when(context.findOne(any(GetQuery.class))).thenReturn(Optional.empty());
            doNothing().when(context).create(any(Destination.class), any(Schema.class));
            doNothing().when(transaction).commitIfActive();
            doNothing().when(transaction).rollbackIfActive();

            // Act
            repository.add(schema, TEST_USER);

            // Assert
            verify(context).create(destinationCaptor.capture(), any(Schema.class));
            Destination destination = destinationCaptor.getValue();

            assertEquals(TEST_PARTITION_ID, destination.getPartitionId());
            assertEquals(TEST_TENANT_NAME, destination.getNamespace().getName());
            assertEquals(OsmRecordsMetadataRepository.SCHEMA_KIND, destination.getKind());
        }
    }

    // ========================================
    // get() Tests
    // ========================================

    @Nested
    @DisplayName("get() Method Tests")
    class GetTests {

        @Test
        @DisplayName("Should return schema when found")
        void get_SchemaExists_ReturnsSchema() {
            // Arrange
            Schema expectedSchema = createSchema(TEST_KIND);
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Collections.singletonList(expectedSchema));

            // Act
            Schema result = repository.get(TEST_KIND);

            // Assert
            assertNotNull(result);
            assertEquals(TEST_KIND, result.getKind());
            verify(context).getResultsAsList(queryCaptor.capture());

            GetQuery<Schema> query = queryCaptor.getValue();
            assertNotNull(query);
        }

        @Test
        @DisplayName("Should return null when schema not found")
        void get_SchemaNotFound_ReturnsNull() {
            // Arrange
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            Schema result = repository.get(TEST_KIND);

            // Assert
            assertNull(result);
            verify(context).getResultsAsList(any(GetQuery.class));
        }

        @Test
        @DisplayName("Should throw NullPointerException when results list is null")
        void get_NullResultsList_ThrowsNullPointerException() {
            // Arrange
            when(context.getResultsAsList(any(GetQuery.class))).thenReturn(null);

            // Act & Assert
            assertThrows(NullPointerException.class, () -> repository.get(TEST_KIND));
        }

        @Test
        @DisplayName("Should return first schema when multiple exist")
        void get_MultipleSchemas_ReturnsFirst() {
            // Arrange
            Schema schema1 = createSchema(TEST_KIND);
            Schema schema2 = createSchema(TEST_KIND);
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Arrays.asList(schema1, schema2));

            // Act
            Schema result = repository.get(TEST_KIND);

            // Assert
            assertNotNull(result);
            assertEquals(schema1, result);
        }

        @Test
        @DisplayName("Should construct query with correct kind")
        void get_ConstructsQueryWithCorrectKind() {
            // Arrange
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            repository.get(TEST_KIND);

            // Assert
            verify(context).getResultsAsList(queryCaptor.capture());
            GetQuery<Schema> query = queryCaptor.getValue();
            assertNotNull(query);
            assertNotNull(query.getWhere());
        }

        @Test
        @DisplayName("Should use correct destination when getting schema")
        void get_UsesCorrectDestination() {
            // Arrange
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            repository.get(TEST_KIND);

            // Assert
            verify(context).getResultsAsList(queryCaptor.capture());
            GetQuery<Schema> query = queryCaptor.getValue();
            Destination destination = query.getDestination();

            assertEquals(TEST_PARTITION_ID, destination.getPartitionId());
            assertEquals(TEST_TENANT_NAME, destination.getNamespace().getName());
            assertEquals(OsmRecordsMetadataRepository.SCHEMA_KIND, destination.getKind());
        }
    }

    // ========================================
    // delete() Tests
    // ========================================

    @Nested
    @DisplayName("delete() Method Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should call deleteById with correct parameters")
        void delete_CallsDeleteByIdWithCorrectParameters() {
            // Arrange
            lenient().doNothing().when(context).deleteById(
                    any(Class.class),
                    any(Destination.class),
                    anyString(),
                    any(String[].class)
            );

            // Act
            repository.delete(TEST_KIND);

            // Assert
            verify(context).deleteById(
                    eq(Schema.class),
                    destinationCaptor.capture(),
                    eq(TEST_KIND),
                    any(String[].class)
            );

            Destination destination = destinationCaptor.getValue();
            assertEquals(TEST_PARTITION_ID, destination.getPartitionId());
            assertEquals(TEST_TENANT_NAME, destination.getNamespace().getName());
            assertEquals(OsmRecordsMetadataRepository.SCHEMA_KIND, destination.getKind());
        }

        @Test
        @DisplayName("Should handle deletion of non-existent schema")
        void delete_NonExistentSchema_CallsDeleteById() {
            // Arrange
            lenient().doNothing().when(context).deleteById(
                    any(Class.class),
                    any(Destination.class),
                    anyString(),
                    any(String[].class)
            );

            // Act
            repository.delete("non-existent-kind");

            // Assert
            verify(context).deleteById(
                    eq(Schema.class),
                    any(Destination.class),
                    eq("non-existent-kind"),
                    any(String[].class)
            );
        }

        @Test
        @DisplayName("Should propagate exception from deleteById")
        void delete_DeleteByIdThrowsException_PropagatesException() {
            // Arrange
            RuntimeException expectedException = new RuntimeException("Delete failed");
            doThrow(expectedException).when(context).deleteById(
                    any(Class.class),
                    any(Destination.class),
                    anyString(),
                    any(String[].class)
            );

            // Act & Assert
            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> repository.delete(TEST_KIND)
            );

            assertEquals(expectedException, thrown);
            verify(context).deleteById(
                    eq(Schema.class),
                    any(Destination.class),
                    eq(TEST_KIND),
                    any(String[].class)
            );
        }

        @Test
        @DisplayName("Should delete with empty kind string")
        void delete_EmptyKind_CallsDeleteById() {
            // Arrange
            lenient().doNothing().when(context).deleteById(
                    any(Class.class),
                    any(Destination.class),
                    anyString(),
                    any(String[].class)
            );

            // Act
            repository.delete("");

            // Assert
            verify(context).deleteById(
                    eq(Schema.class),
                    any(Destination.class),
                    eq(""),
                    any(String[].class)
            );
        }

        @Test
        @DisplayName("Should delete with null kind")
        void delete_NullKind_CallsDeleteById() {
            // Arrange
            lenient().doNothing().when(context).deleteById(
                    any(Class.class),
                    any(Destination.class),
                    anyString(),
                    any(String[].class)
            );

            // Act
            repository.delete(null);

            // Assert
            verify(context).deleteById(
                    eq(Schema.class),
                    any(Destination.class),
                    isNull(),
                    any(String[].class)
            );
        }
    }

    // ========================================
    // Destination Building Tests
    // ========================================

    @Nested
    @DisplayName("Destination Building Tests")
    class DestinationBuildingTests {

        @Test
        @DisplayName("Should build destination with correct partition ID")
        void getDestination_IncludesCorrectPartitionId() {
            // Arrange
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            repository.get(TEST_KIND);

            // Assert
            verify(context).getResultsAsList(queryCaptor.capture());
            Destination destination = queryCaptor.getValue().getDestination();
            assertEquals(TEST_PARTITION_ID, destination.getPartitionId());
        }

        @Test
        @DisplayName("Should build destination with correct namespace")
        void getDestination_IncludesCorrectNamespace() {
            // Arrange
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            repository.get(TEST_KIND);

            // Assert
            verify(context).getResultsAsList(queryCaptor.capture());
            Destination destination = queryCaptor.getValue().getDestination();
            assertNotNull(destination.getNamespace());
            assertEquals(TEST_TENANT_NAME, destination.getNamespace().getName());
        }

        @Test
        @DisplayName("Should build destination with correct kind")
        void getDestination_IncludesCorrectKind() {
            // Arrange
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            repository.get(TEST_KIND);

            // Assert
            verify(context).getResultsAsList(queryCaptor.capture());
            Destination destination = queryCaptor.getValue().getDestination();
            assertEquals(OsmRecordsMetadataRepository.SCHEMA_KIND, destination.getKind());
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private Schema createSchema(String kind) {
        Schema schema = new Schema();
        schema.setKind(kind);
        return schema;
    }
}
