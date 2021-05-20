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

package org.opengroup.osdu.storage.provider.azure.api;

import com.azure.spring.autoconfigure.aad.UserPrincipal;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jose.JWSHeader.Builder;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import net.minidev.json.JSONArray;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.Assert.assertEquals;

@Ignore("Integration tests that mock AAD but require settings for Azure infra")
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SchemaEndToEndTest {
    @Autowired
    private MockMvc mockMvc;

    @Mock
    Authentication auth;

    @Mock
    SecurityContext securityContext;

    static final String upnAdmin = "a@foo.com";
    static final String upnViewer = "v@foo.com";
    static final String unixTime = String.valueOf(System.currentTimeMillis() / 1000L);
    static final String schemaApiEndpoint = "/schemas/";
    static final String tenant1 = "common";
    static final String kind = tenant1 + ":unittest:u" + unixTime + ":1.0.0";
    static final String tenant2 = "opendes";
    static final String schemaContent = "{" +
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

    private UserPrincipal createAADUserPrincipal(Collection<String> roles, String upn) {
        final JSONArray claims = new JSONArray();
        claims.addAll(roles);
        final JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(upn)
                .claim("roles", claims)
                .claim("upn", upn)
                .build();
        final JWSObject jwsObject = new JWSObject(new Builder(JWSAlgorithm.RS256).build(),
                new Payload(jwtClaimsSet.toString()));
        return  new UserPrincipal(upn, jwsObject, jwtClaimsSet);
    }

    private void setupUser(String upn, String appRole, String serviceRole) {
        UserPrincipal dummyAADPrincipal = createAADUserPrincipal(
            //app roles to be used in entitlement
            Arrays.asList(appRole),
            upn
        );

        List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>() {
            {//Spring security roles to be used in @Secured controllers
                add(new SimpleGrantedAuthority(serviceRole));
            }
        };

        doReturn(authorities).when(auth).getAuthorities();
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(dummyAADPrincipal);
        when(securityContext.getAuthentication()).thenReturn(auth);
    }

    private String validPostBody(String kind) {

        JsonObject item1Ext = new JsonObject();
        item1Ext.addProperty("indexerTip", "call911");

        JsonObject item1 = new JsonObject();
        item1.addProperty("path", "name");
        item1.addProperty("kind", "string");
        item1.add("ext", item1Ext);

        JsonObject item2Ext = new JsonObject();
        item2Ext.addProperty("address.city", "this is a weird string");
        item2Ext.addProperty("address.country", "country with two letters");

        JsonObject item2 = new JsonObject();
        item2.addProperty("path", "age");
        item2.addProperty("kind", "int");
        item2.add("ext", item2Ext);

        JsonArray schemaItems = new JsonArray();
        schemaItems.add(item1);
        schemaItems.add(item2);

        JsonObject schema = new JsonObject();
        schema.addProperty("kind", kind);
        schema.add("schema", schemaItems);
        schema.add("ext", item2Ext);

        return schema.toString();
    }

    @Test //this test must execute first with default lexicographical order, hence "1"Admin
    public void given1Admin_whenCreateSchema_thenCreated() throws Exception {
        setupUser(upnAdmin, StorageRole.ADMIN, StorageRole.ROLE_ADMIN);
        RequestBuilder createSchemaRequest = MockMvcRequestBuilders
                .post(schemaApiEndpoint)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .header(DpsHeaders.AUTHORIZATION, upnAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(schemaContent);
        mockMvc.perform(createSchemaRequest).andExpect(status().isCreated());
    }

    @Test
    public void given2SameTenant_whenAccessSchema_thenSuccess() throws Exception {
        setupUser(upnAdmin, StorageRole.ADMIN, StorageRole.ROLE_ADMIN);
        RequestBuilder getSchemaRequest = MockMvcRequestBuilders
                .get(schemaApiEndpoint + kind)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .header(DpsHeaders.AUTHORIZATION, upnAdmin)
                .accept(MediaType.APPLICATION_JSON);
        mockMvc.perform(getSchemaRequest).andExpect(status().isOk());
    }

    @Test
    public void given2DifferentTenant_whenAccessSchema_thenForbidden() throws Exception {
        setupUser(upnAdmin, StorageRole.ADMIN, StorageRole.ROLE_ADMIN);
        RequestBuilder getSchemaRequest = MockMvcRequestBuilders
                .get(schemaApiEndpoint + kind)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant2)
                .header(DpsHeaders.AUTHORIZATION, upnAdmin)
                .accept(MediaType.APPLICATION_JSON);
        mockMvc.perform(getSchemaRequest).andExpect(status().isForbidden());
    }

    @Test
    public void given2Viewer_whenCreateSchema_thenForbidden() throws Exception {
        setupUser(upnViewer, StorageRole.VIEWER, StorageRole.ROLE_VIEWER);
        RequestBuilder createSchemaRequest = MockMvcRequestBuilders
                .post(schemaApiEndpoint)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .header(DpsHeaders.AUTHORIZATION, upnViewer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(schemaContent);
        mockMvc.perform(createSchemaRequest).andExpect(status().isForbidden());
    }

    @Test
    public void given3Admin_whenDeleteSchema_thenDeleted() throws Exception {
        setupUser(upnAdmin, StorageRole.ADMIN, StorageRole.ROLE_ADMIN);
        RequestBuilder deleteSchemaRequest = MockMvcRequestBuilders
                .delete(schemaApiEndpoint + kind)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .header(DpsHeaders.AUTHORIZATION, upnAdmin)
                .accept(MediaType.APPLICATION_JSON);
        mockMvc.perform(deleteSchemaRequest).andExpect(status().isNoContent());

        RequestBuilder getSchemaRequest = MockMvcRequestBuilders
                .get(schemaApiEndpoint + kind)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .header(DpsHeaders.AUTHORIZATION, upnAdmin)
                .accept(MediaType.APPLICATION_JSON);
        mockMvc.perform(getSchemaRequest).andExpect(status().isNotFound());
    }

    @Test
    public void should_createSchema_and_returnHttp409IfTryToCreateItAgain_and_getSchema_and_deleteSchema_when_providingValidSchemaInfo()
            throws Exception {
        final String intKind = tenant1 + ":storage:inttest:1.0.0" + System.currentTimeMillis();
        String body = this.validPostBody(intKind);

        // Create schema
        setupUser(upnAdmin, StorageRole.ADMIN, StorageRole.ROLE_ADMIN);
        RequestBuilder createSchemaRequest = MockMvcRequestBuilders
                .post(schemaApiEndpoint)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .header(DpsHeaders.AUTHORIZATION, "anything")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        mockMvc.perform(createSchemaRequest).andExpect(status().isCreated());

        // Try to create again
        createSchemaRequest = MockMvcRequestBuilders
                .post(schemaApiEndpoint)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .header(DpsHeaders.AUTHORIZATION, "anything")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        mockMvc.perform(createSchemaRequest).andExpect(status().isConflict());

        // Get the schema
        RequestBuilder getSchemaRequest = MockMvcRequestBuilders
                .get(schemaApiEndpoint + intKind)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .header(DpsHeaders.AUTHORIZATION, "anything")
                .accept(MediaType.APPLICATION_JSON);
        MvcResult result = mockMvc.perform(getSchemaRequest).andExpect(status().isOk()).andReturn();
        String responseBody = result.getResponse().getContentAsString();

        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(responseBody).getAsJsonObject();

        assertEquals(intKind, json.get("kind").getAsString());
        assertEquals(2, json.get("schema").getAsJsonArray().size());
        assertEquals("name", json.get("schema").getAsJsonArray().get(0).getAsJsonObject().get("path").getAsString());
        assertEquals("string", json.get("schema").getAsJsonArray().get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals("call911", json.get("schema").getAsJsonArray().get(0).getAsJsonObject().get("ext")
                .getAsJsonObject().get("indexerTip").getAsString());

        assertEquals("age", json.get("schema").getAsJsonArray().get(1).getAsJsonObject().get("path").getAsString());
        assertEquals("int", json.get("schema").getAsJsonArray().get(1).getAsJsonObject().get("kind").getAsString());

        assertEquals(2, json.get("ext").getAsJsonObject().size());
        assertEquals("this is a weird string", json.get("ext").getAsJsonObject().get("address.city").getAsString());
        assertEquals("country with two letters",
                json.get("ext").getAsJsonObject().get("address.country").getAsString());

        // Delete schema
        RequestBuilder deleteSchemaRequest = MockMvcRequestBuilders
                .delete(schemaApiEndpoint + intKind)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .header(DpsHeaders.AUTHORIZATION, "anything")
                .accept(MediaType.APPLICATION_JSON);
        mockMvc.perform(deleteSchemaRequest).andExpect(status().isNoContent());

        getSchemaRequest = MockMvcRequestBuilders
                .get(schemaApiEndpoint + intKind)
                .with(securityContext(securityContext))
                .header(DpsHeaders.DATA_PARTITION_ID, tenant1)
                .header(DpsHeaders.AUTHORIZATION, "anything")
                .accept(MediaType.APPLICATION_JSON);
        mockMvc.perform(getSchemaRequest).andExpect(status().isNotFound());
    }
}
