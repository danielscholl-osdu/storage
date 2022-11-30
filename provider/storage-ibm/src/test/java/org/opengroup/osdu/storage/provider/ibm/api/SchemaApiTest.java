/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.storage.provider.ibm.api;

import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.storage.api.SchemaApi;
import org.opengroup.osdu.storage.service.SchemaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

@Ignore
@RunWith(SpringRunner.class)
@SpringBootTest(classes={StorageApplication.class})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SchemaApiTest {
    @Mock
    private SchemaService schemaService;

    @InjectMocks
    @Autowired //this causes the Spring app to start, otherwise, it's just a mocked object
    private SchemaApi sut;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test(expected = AuthenticationCredentialsNotFoundException.class)
    public void givenUnauthenticated_whenCallCreateSchema_thenThrowsException(){
        Schema schema = createTestSchema();
        this.sut.createSchema(schema);
    }

    @WithMockUser(username="admin", roles={StorageRole.ADMIN})
    @Test
    public void given1AuthenticatedAdmin_whenCallCreateSchema_thenOk() {
        Schema schema = createTestSchema();
        Assert.assertEquals(HttpStatus.CREATED, this.sut.createSchema(schema).getStatusCode());
    }

    @WithMockUser(username="viewer", roles={StorageRole.VIEWER})
    @Test
    public void givenAuthenticatedViewer_whenCallCreateSchema_thenForbidden() {
        try {
            Schema schema = createTestSchema();
            this.sut.createSchema(schema);
        } catch (AppException e) {
            Assert.assertEquals(HttpStatus.FORBIDDEN.value(), e.getError().getCode());
        }
    }

    @WithMockUser(username="viewer", roles={StorageRole.VIEWER})
    @Test
    public void given2AuthenticatedViewer_whenCallGetSchema_thenOK() {
        String kind = "opendes:source:type:1.0.0";
        Assert.assertEquals(HttpStatus.OK, this.sut.getSchema(kind).getStatusCode());
    }

    private Schema createTestSchema() {
        Schema schema = new Schema();
        schema.setKind("opendes:source:type:1.0.1");
        SchemaItem item = new SchemaItem();
        item.setKind("schemaKind");
        item.setPath("schemaPath");
        SchemaItem[] schemaItems = new SchemaItem[1];
        schemaItems[0] = item;
        schema.setSchema(schemaItems);
        return schema;
    }
}
