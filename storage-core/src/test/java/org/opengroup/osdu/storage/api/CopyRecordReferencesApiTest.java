/*
 *    Copyright (c) 2024. EPAM Systems, Inc
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.opengroup.osdu.storage.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.model.CopyRecordReferencesModel;
import org.opengroup.osdu.storage.service.CopyRecordReferencesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = CopyRecordReferencesApi.class)
@WebMvcTest
class CopyRecordReferencesApiTest {

  private static final String COPY_RECORDS_PATH = "/records/copy";

  @Autowired
  private MockMvc mockMvc;
  @MockBean
  private CopyRecordReferencesService service;
  private final Gson gson = new Gson();

  @Test
  void should_return_status_200_when_get_project() throws Exception {
    CopyRecordReferencesModel request = CopyRecordReferencesModel.builder()
        .target("a99cef48-2ed6-4beb-8a43-002373431f13").build();
    mockMvc.perform(put(COPY_RECORDS_PATH)
            .header("x-collaboration", "id=a99cef48-2ed6-4beb-8a43-002373431f13,application=pws")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(request)))
        .andExpect(status().isOk());
  }
}

