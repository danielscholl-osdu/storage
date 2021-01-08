// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.provider.byoc.api;

import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;

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

    @Test //this test must execute first with default lexicographical order, hence "1"Admin
    public void given1Admin_whenCreateSchema_thenCreated() throws Exception {
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
