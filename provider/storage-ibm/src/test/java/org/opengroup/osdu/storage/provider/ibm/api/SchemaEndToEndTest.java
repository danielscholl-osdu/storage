/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/
package org.opengroup.osdu.storage.provider.ibm.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@Ignore
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SchemaEndToEndTest {
    
	@Autowired
    private MockMvc mockMvc;

    final UserRequestPostProcessor adminUser = user("a").roles(StorageRole.ADMIN);
    final UserRequestPostProcessor viewerUser = user("v").roles(StorageRole.VIEWER);

    final String schemaApiEndpoint = "/schemas/";
    final String tenant1 = "common";
    final String kind = tenant1 + ":welldb:wellbore:1.0.0";
    final String tenant2 = "opendes";
    final String schemaContent = "{" +
                    "\"ext\":{}," +
                    "\"kind\": \"" + kind + "\", " +
                    "\"schema\": [" +
                    "    {" +
                    "      \"ext\": {}," +
                    "      \"kind\": \"string\"," +
                    "      \"path\": \"string\"" +
                    "    }" +
                    "  ]" +
                    "}";
    
    public void deleteKindAsAdmin() {
    	RequestBuilder deleteSchemaRequest = MockMvcRequestBuilders
                .delete(schemaApiEndpoint + kind)
                .with(adminUser)
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1);
        try {
			mockMvc.perform(deleteSchemaRequest);
		} catch (Exception e) {
			System.err.println("Error deleting schema " + kind);
			e.printStackTrace();
		}
    }
    
    @Test //this test must execute first with default lexicographical order, hence "1"Admin
    public void given1Admin_whenCreateSchema_thenCreated() throws Exception {
        this.deleteKindAsAdmin();
        RequestBuilder createSchemaRequest = MockMvcRequestBuilders
                .post(schemaApiEndpoint)
                .with(adminUser)
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(schemaContent);
        mockMvc.perform(createSchemaRequest).andExpect(status().isCreated());
    }

    @Test
    public void givenDifferentTenant_whenAccessSchema_thenSuccess() throws Exception{
        RequestBuilder getSchemaRequest = MockMvcRequestBuilders
                .get(schemaApiEndpoint + kind)
                .with(viewerUser)
                .header(DpsHeaders.DATA_PARTITION_ID, tenant2)
                .accept(MediaType.APPLICATION_JSON);
        mockMvc.perform(getSchemaRequest).andExpect(status().isOk());
    }

    @Test
    public void givenSameTenant_whenAccessSchema_thenSuccess() throws Exception {
        RequestBuilder getSchemaRequest = MockMvcRequestBuilders
                .get(schemaApiEndpoint + kind)
                .with(viewerUser)
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .accept(MediaType.APPLICATION_JSON);
        mockMvc.perform(getSchemaRequest).andExpect(status().isOk());
    }
    
}
