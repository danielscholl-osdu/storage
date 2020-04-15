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

package org.opengroup.osdu.storage.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.lang.reflect.Method;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;

import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.service.SchemaService;
import org.springframework.http.ResponseEntity;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.access.prepost.PreAuthorize;

@RunWith(MockitoJUnitRunner.class)
public class SchemaApiTest {

    private final String USER = "testUser";

    @Mock
    private SchemaService schemaService;

    @Mock
    private DpsHeaders httpHeaders;

    @InjectMocks
    private SchemaApi sut;

    @Before
    public void setup() {
        initMocks(this);

        when(this.httpHeaders.getUserEmail()).thenReturn(this.USER);

    }

    @Test
    public void should_returnHttp201_when_creatingSchemaSuccessfully() {
        final String USER = "testUser@gmail.com";

        Schema schema = new Schema();
        schema.setKind("any kind");

        this.schemaService.createSchema(schema);

        ResponseEntity response = this.sut.createSchema(schema);

        assertEquals(HttpStatus.SC_CREATED, response.getStatusCodeValue());
    }

    @Test
    public void should_returnHttp200_when_gettingSchemaSuccessfully() {
        final String KIND = "anyKind";

        Schema schema = new Schema();
        schema.setKind(KIND);

        when(this.schemaService.getSchema(KIND)).thenReturn(schema);

        ResponseEntity response = this.sut.getSchema(KIND);

        Schema schemaResponse = (Schema) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertEquals(KIND, schemaResponse.getKind());
    }

    @Test
    public void should_returnHttp204_when_deletingSchemaSuccessfully() {
        final String KIND = "anyKind";

        ResponseEntity response = this.sut.deleteSchema(KIND);

        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_allowAccessToCreateSchema_when_userBelongsToCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("createSchema", Schema.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToGetSchema_when_userBelongsToViewerCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("getSchema", String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertTrue(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToDeleteSchema_when_userBelongsToAdminGroup() throws Exception {

        Method method = this.sut.getClass().getMethod("deleteSchema", String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertTrue(annotation.value().contains(StorageRole.ADMIN));
        assertTrue(!annotation.value().contains(StorageRole.CREATOR));
        assertTrue(!annotation.value().contains(StorageRole.VIEWER));
    }
}
