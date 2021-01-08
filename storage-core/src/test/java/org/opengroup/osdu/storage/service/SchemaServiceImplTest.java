// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.storage.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ConcurrentModificationException;
import java.util.HashMap;

import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SchemaServiceImplTest {

    private static final String USER = null;
    private static final String TENANT_NAME = "TENANT1";
    private static final String KIND = "tenant1:test:unit:1.0.0";

    @Mock
    private ISchemaRepository schemaRepository;

    @Mock
    private ICache<String, Schema> cacheService;

    @Mock
    private ITenantFactory tenantFactory;

    @Mock
    private TenantInfo tenant;

    @Mock
    private IMessageBus pubSubClient;

    @Mock
    private DpsHeaders headers;

    @Mock
    private StorageAuditLogger auditLogger;

    @InjectMocks
    private SchemaServiceImpl sut;

    @Before
    public void setup() {
        when(this.headers.getPartitionIdWithFallbackToAccountId()).thenReturn(TENANT_NAME);
        when(this.tenant.getName()).thenReturn(TENANT_NAME);
        when(this.tenantFactory.exists(TENANT_NAME)).thenReturn(true);
        when(this.tenantFactory.getTenantInfo(TENANT_NAME)).thenReturn(this.tenant);
    }

    @Test
    public void should_returnHttp400_when_creatingSchemaWithInvalidSchemaItemKind() {

        Schema schema = new Schema();
        schema.setKind(KIND);
        schema.setSchema(new SchemaItem[] { new SchemaItem("anyPath", "invalidKind", new HashMap<>()) });

        try {
            this.sut.createSchema(schema);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid schema", e.getError().getReason());
            assertEquals("Schema item 'anyPath' has an invalid data type 'invalidkind'", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_returnHttp400_when_creatingSchemaWithCircularReference() {

        Schema schema = new Schema();
        schema.setKind(KIND);
        schema.setSchema(new SchemaItem[] { new SchemaItem("anyPath", "[]" + KIND, new HashMap<>()) });

        try {
            this.sut.createSchema(schema);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid schema", e.getError().getReason());
            assertEquals("Found circular reference kind: 'tenant1:test:unit:1.0.0' Schema list: [tenant1:test:unit:1.0.0]",
                    e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_createSchemaSuccessfully_when_noValidationErrorsAreFound() {

        Schema schema = new Schema();
        schema.setKind(KIND);
        schema.setSchema(new SchemaItem[] { new SchemaItem("anyPath", "integer", new HashMap<>()) });

        this.sut.createSchema(schema);

        verify(this.auditLogger).createSchemaSuccess(any());

        verify(this.schemaRepository).add(schema, USER);
        verify(this.cacheService).put("EUerYg==", schema);
        verify(this.pubSubClient).publishMessage(this.headers,
                new PubSubInfo(null, KIND, OperationType.create_schema));
    }

    @Test
    public void should_returnHttp409_when_createdSchemaAlreadyExists() {

        Schema schema = new Schema();
        schema.setKind(KIND);
        schema.setSchema(new SchemaItem[] { new SchemaItem("anyPath", "integer", new HashMap<>()) });

        doThrow(new IllegalArgumentException()).when(this.schemaRepository).add(schema, USER);

        try {
            this.sut.createSchema(schema);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_CONFLICT, e.getError().getCode());
            assertEquals("Schema already registered", e.getError().getReason());
            assertEquals("The schema information for the given kind already exists.", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_returnHttp409_when_schemaIsBeingModifiedAtTheSameTime() {

        Schema schema = new Schema();
        schema.setKind(KIND);
        schema.setSchema(new SchemaItem[] { new SchemaItem("anyPath", "integer", new HashMap<>()) });

        doThrow(new ConcurrentModificationException()).when(this.schemaRepository).add(schema, USER);

        try {
            this.sut.createSchema(schema);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_CONFLICT, e.getError().getCode());
            assertEquals("Schema already registered", e.getError().getReason());
            assertEquals("Concurrent schema modification error.", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_returnHttp500_when_otherExceptionOccur() {

        Schema schema = new Schema();
        schema.setKind(KIND);
        schema.setSchema(new SchemaItem[] { new SchemaItem("anyPath", "integer", new HashMap<>()) });

        doThrow(new NullPointerException()).when(this.schemaRepository).add(schema, USER);

        try {
            this.sut.createSchema(schema);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getError().getCode());
            assertEquals("Error on schema creation", e.getError().getReason());
            assertEquals("An unknown error occurred during schema creation.", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_deleteSchema_when_noValidationErrorOccurs() {
        when(this.schemaRepository.get(KIND)).thenReturn(new Schema());

        this.sut.deleteSchema(KIND);

        verify(this.auditLogger).deleteSchemaSuccess(any());

        verify(this.schemaRepository).delete(KIND);
        verify(this.cacheService).delete("EUerYg==");
    }

    @Test
    public void should_returnHttp400_when_deletingASchemaWhichDoesNotExist() {
        when(this.schemaRepository.get(KIND)).thenReturn(null);

        try {
            this.sut.deleteSchema(KIND);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getError().getCode());
            assertEquals("Schema not found", e.getError().getReason());
            assertEquals("Schema not registered for kind 'tenant1:test:unit:1.0.0'", e.getError().getMessage());
        }
    }

    @Test
    public void should_getSchemaFromCache_when_schemaIsCached() {
        Schema schema = new Schema();
        schema.setKind(KIND);

        when(this.cacheService.get("EUerYg==")).thenReturn(schema);

        Schema foundSchema = this.sut.getSchema(KIND);
        assertEquals(schema, foundSchema);
    }

    @Test
    public void should_getSchemaFromDatastore_when_schemaIsNotInCache() {
        Schema schema = new Schema();
        schema.setKind(KIND);

        when(this.cacheService.get(KIND)).thenReturn(null);

        when(this.schemaRepository.get(KIND)).thenReturn(schema);

        Schema foundSchema = this.sut.getSchema(KIND);
        assertEquals(schema, foundSchema);

        verify(this.auditLogger).readSchemaSuccess(any());

        verify(this.cacheService).put("EUerYg==", schema);
    }

    @Test
    public void should_returnHttp404_when_gettingSchemaWhichDoesNotExist() {
        when(this.cacheService.get(KIND)).thenReturn(null);

        when(this.schemaRepository.get(KIND)).thenReturn(null);

        try {
            this.sut.getSchema(KIND);

            verify(this.auditLogger).readSchemaSuccess(any());

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getError().getCode());
            assertEquals("Schema not found", e.getError().getReason());
            assertEquals("Schema not registered for kind 'tenant1:test:unit:1.0.0'", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_succeedSchemaValidation_when_validDataTypesAreProvided() {

        SchemaItem item1 = new SchemaItem("age", "integer", new HashMap<>());
        SchemaItem item2 = new SchemaItem("year", "int", new HashMap<>());
        SchemaItem item3 = new SchemaItem("senior", "bool", new HashMap<>());
        SchemaItem item4 = new SchemaItem("junior", "boolean", new HashMap<>());
        SchemaItem item5 = new SchemaItem("depth", "float", new HashMap<>());
        SchemaItem item6 = new SchemaItem("height", "double", new HashMap<>());
        SchemaItem item7 = new SchemaItem("width", "long", new HashMap<>());
        SchemaItem item8 = new SchemaItem("name", "string", new HashMap<>());
        SchemaItem item9 = new SchemaItem("reference", "link", new HashMap<>());
        SchemaItem item10 = new SchemaItem("dob", "datetime", new HashMap<>());
        SchemaItem item11 = new SchemaItem("location", "core:dl:geopoint:1.0.0", new HashMap<>());
        SchemaItem item12 = new SchemaItem("shape", "core:dl:geoshape:1.0.0", new HashMap<>());
        SchemaItem item13 = new SchemaItem("age-array", "[]integer", new HashMap<>());
        SchemaItem item14 = new SchemaItem("year-array", "[]int", new HashMap<>());
        SchemaItem item15 = new SchemaItem("senior-array", "[]bool", new HashMap<>());
        SchemaItem item16 = new SchemaItem("junior-array", "[]boolean", new HashMap<>());
        SchemaItem item17 = new SchemaItem("depth-array", "[]float", new HashMap<>());
        SchemaItem item18 = new SchemaItem("height-array", "[]double", new HashMap<>());
        SchemaItem item19 = new SchemaItem("width-array", "[]long", new HashMap<>());
        SchemaItem item20 = new SchemaItem("name-array", "[]string", new HashMap<>());
        SchemaItem item21 = new SchemaItem("reference-array", "[]link", new HashMap<>());
        SchemaItem item22 = new SchemaItem("dob-array", "[]datetime", new HashMap<>());
        SchemaItem item23 = new SchemaItem("location-array", "[]core:dl:geopoint:1.0.0", new HashMap<>());
        SchemaItem item24 = new SchemaItem("shape-array", "[]core:dl:geoshape:1.0.0", new HashMap<>());

        Schema schema = new Schema();
        schema.setKind("test:kind:1.0.0");
        schema.setSchema(new SchemaItem[] { item1, item2, item3, item4, item5, item6, item7, item8, item9, item10,
                item11, item12, item13, item14, item15, item16, item17, item18, item19, item20, item21, item22, item23,
                item24 });

        Schema result = this.sut.validateSchema(schema);
        assertEquals("age", result.getSchema()[0].getPath());
        assertEquals("int", result.getSchema()[0].getKind());
        assertEquals("year", result.getSchema()[1].getPath());
        assertEquals("int", result.getSchema()[1].getKind());
        assertEquals("senior", result.getSchema()[2].getPath());
        assertEquals("boolean", result.getSchema()[2].getKind());
        assertEquals("junior", result.getSchema()[3].getPath());
        assertEquals("boolean", result.getSchema()[3].getKind());
        assertEquals("depth", result.getSchema()[4].getPath());
        assertEquals("float", result.getSchema()[4].getKind());
        assertEquals("height", result.getSchema()[5].getPath());
        assertEquals("double", result.getSchema()[5].getKind());
        assertEquals("width", result.getSchema()[6].getPath());
        assertEquals("long", result.getSchema()[6].getKind());
        assertEquals("name", result.getSchema()[7].getPath());
        assertEquals("string", result.getSchema()[7].getKind());
        assertEquals("reference", result.getSchema()[8].getPath());
        assertEquals("link", result.getSchema()[8].getKind());
        assertEquals("dob", result.getSchema()[9].getPath());
        assertEquals("datetime", result.getSchema()[9].getKind());
        assertEquals("location", result.getSchema()[10].getPath());
        assertEquals("core:dl:geopoint:1.0.0", result.getSchema()[10].getKind());
        assertEquals("shape", result.getSchema()[11].getPath());
        assertEquals("core:dl:geoshape:1.0.0", result.getSchema()[11].getKind());

        assertEquals("age-array", result.getSchema()[12].getPath());
        assertEquals("[]int", result.getSchema()[12].getKind());
        assertEquals("year-array", result.getSchema()[13].getPath());
        assertEquals("[]int", result.getSchema()[13].getKind());
        assertEquals("senior-array", result.getSchema()[14].getPath());
        assertEquals("[]boolean", result.getSchema()[14].getKind());
        assertEquals("junior-array", result.getSchema()[15].getPath());
        assertEquals("[]boolean", result.getSchema()[15].getKind());
        assertEquals("depth-array", result.getSchema()[16].getPath());
        assertEquals("[]float", result.getSchema()[16].getKind());
        assertEquals("height-array", result.getSchema()[17].getPath());
        assertEquals("[]double", result.getSchema()[17].getKind());
        assertEquals("width-array", result.getSchema()[18].getPath());
        assertEquals("[]long", result.getSchema()[18].getKind());
        assertEquals("name-array", result.getSchema()[19].getPath());
        assertEquals("[]string", result.getSchema()[19].getKind());
        assertEquals("reference-array", result.getSchema()[20].getPath());
        assertEquals("[]link", result.getSchema()[20].getKind());
        assertEquals("dob-array", result.getSchema()[21].getPath());
        assertEquals("[]datetime", result.getSchema()[21].getKind());
        assertEquals("location-array", result.getSchema()[22].getPath());
        assertEquals("[]core:dl:geopoint:1.0.0", result.getSchema()[22].getKind());
        assertEquals("shape-array", result.getSchema()[23].getPath());
        assertEquals("[]core:dl:geoshape:1.0.0", result.getSchema()[23].getKind());
    }

    @Test
    public void should_throwExceptionAndReturnHttp400_when_validatingASchemaWithInvalidDataType() {

        SchemaItem item = new SchemaItem("whatever", "weird", new HashMap<>());

        Schema schema = new Schema();
        schema.setKind("test:kind:1.0.0");
        schema.setSchema(new SchemaItem[] { item });

        try {
            this.sut.validateSchema(schema);
            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid schema", e.getError().getReason());
            assertEquals("Schema item 'whatever' has an invalid data type 'weird'", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_throwExceptionAndReturnHttp400_when_validatingASchemaWithInvalidArrayNotationInDataType() {

        SchemaItem item = new SchemaItem("whatever", "int[]", new HashMap<>());

        Schema schema = new Schema();
        schema.setKind("test:kind:1.0.0");
        schema.setSchema(new SchemaItem[] { item });

        try {
            this.sut.validateSchema(schema);
            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid schema", e.getError().getReason());
            assertEquals("Schema item invalid for path 'whatever': array types must start with '[]'",
                    e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_throwExceptionAndReturnHttp400_when_validatingASchemaWhereAnotherDataLakeKindIsUsedAsDataTypeButInAnInvalidFormat() {
        SchemaItem item1 = new SchemaItem("LatLong", "dl:geopoint:version", new HashMap<>());
        SchemaItem item2 = new SchemaItem("MapSymbol", "int", new HashMap<>());
        SchemaItem item3 = new SchemaItem("ProjDepth", "float", new HashMap<>());
        SchemaItem item4 = new SchemaItem("SpudDate", "datetime", new HashMap<>());
        SchemaItem item5 = new SchemaItem("FirstSpudDate", "datetime", new HashMap<>());
        SchemaItem item6 = new SchemaItem("CompDate", "datetime", new HashMap<>());
        SchemaItem item7 = new SchemaItem("PermitLicenseDate", "datetime", new HashMap<>());
        SchemaItem item8 = new SchemaItem("LastActivityDate", "datetime", new HashMap<>());

        Schema schema = new Schema();
        schema.setKind("ihs:well:1.0.0");
        schema.setSchema(new SchemaItem[] { item2, item3, item4, item5, item6, item7, item8, item1 });

        try {
            this.sut.validateSchema(schema);
            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid schema", e.getError().getReason());
            assertEquals("Schema item 'LatLong' has an invalid data type 'dl:geopoint:version'",
                    e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }
}