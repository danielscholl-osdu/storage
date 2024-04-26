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

package org.opengroup.osdu.storage.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.MultiRecordIds;
import org.opengroup.osdu.core.common.model.storage.MultiRecordInfo;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.opengroup.osdu.storage.model.CopyRecordReferencesModel;
import org.opengroup.osdu.storage.model.RecordVersionModel;

@ExtendWith(MockitoExtension.class)
class CopyRecordReferencesServiceTest {

  private static final String NAMESPACE = UUID.randomUUID().toString();
  private static final String ID = "any:test:id:1398374824";
  private static final String COLLABORATION_DIRECTIVE_WITHOUT_ID = "application=pws";
  private static String COLLABORATION_DIRECTIVE_WITH_ID;

  @Mock
  private BatchService batchService;
  @Mock
  private CollaborationContextFactory collaborationContextFactory;
  @Mock
  private IngestionService ingestionService;
  @InjectMocks
  private CopyRecordReferencesServiceImpl service;

  @BeforeAll
  public static void classSetup() {
    COLLABORATION_DIRECTIVE_WITH_ID= String.format("id=%s,application=pws", NAMESPACE);
  }

  @Test
  void should_return_valid_response() {
    CopyRecordReferencesModel request = getCopyRecordReferencesModel("");
    Record record = new Record();
    record.setId(ID);
    MultiRecordIds multiRecordIds = getRecordIds(request);
    MultiRecordInfo multiRecordInfoWithRecord = new MultiRecordInfo();
    multiRecordInfoWithRecord.setRecords(List.of(record));
    MultiRecordInfo multiRecordInfoWithInvalidRecord = new MultiRecordInfo();
    multiRecordInfoWithInvalidRecord.setInvalidRecords(List.of("test"));

    when(collaborationContextFactory.create(COLLABORATION_DIRECTIVE_WITH_ID)).thenReturn(
        getCollaborationContext());
    when(batchService.getMultipleRecords(any(), any())).thenReturn(multiRecordInfoWithRecord);
    when(batchService.getMultipleRecords(multiRecordIds, Optional.empty())).thenReturn(multiRecordInfoWithInvalidRecord);

    CopyRecordReferencesModel response = service.copyRecordReferences(request, COLLABORATION_DIRECTIVE_WITH_ID);
    assertEquals(request, response);
  }

  @Test
  void should_throwHttp409_when_record_exist_in_target() {
    CopyRecordReferencesModel request = getCopyRecordReferencesModel("");
    Record record = new Record();
    record.setId(ID);
    MultiRecordIds multiRecordIds = getRecordIds(request);
    MultiRecordInfo multiRecordInfo = new MultiRecordInfo();
    multiRecordInfo.setRecords(List.of(record));

    when(collaborationContextFactory.create(COLLABORATION_DIRECTIVE_WITH_ID)).thenReturn(
        getCollaborationContext());
    when(batchService.getMultipleRecords(any(), any())).thenReturn(multiRecordInfo);
    when(batchService.getMultipleRecords(multiRecordIds, Optional.empty())).thenReturn(multiRecordInfo);

    assertThatThrownBy(() ->
        service.copyRecordReferences(request, COLLABORATION_DIRECTIVE_WITH_ID))
        .isInstanceOf(AppException.class)
        .satisfies(e -> {
          AppException appException = (AppException) e;
          assertEquals(409, appException.getError().getCode());
          assertEquals("Records already exists", appException.getError().getReason());
          assertEquals(
              "One or more references already exist in the target namespace: [Record(id=any:test:id:1398374824, version=null, kind=null, acl=null, legal=null, data=null, ancestry=null, meta=null, tags={}, createUser=null, createTime=null, modifyUser=null, modifyTime=null)]",
              appException.getError().getMessage());
        });
  }

  @Test
  void should_throwHttp404_when_record_absent_in_source() {
    MultiRecordInfo multiRecordInfo = new MultiRecordInfo();
    multiRecordInfo.setInvalidRecords(List.of("test"));

    when(collaborationContextFactory.create(COLLABORATION_DIRECTIVE_WITH_ID)).thenReturn(
        getCollaborationContext());
    when(batchService.getMultipleRecords(any(), any(Optional.class))).thenReturn(multiRecordInfo);

    assertThatThrownBy(() ->
        service.copyRecordReferences(getCopyRecordReferencesModel(NAMESPACE),
            COLLABORATION_DIRECTIVE_WITHOUT_ID))
        .isInstanceOf(AppException.class)
        .satisfies(e -> {
          AppException appException = (AppException) e;
          assertEquals(404, appException.getError().getCode());
          assertEquals("Records not found", appException.getError().getReason());
          assertEquals("Source records not found: [test]", appException.getError().getMessage());
        });
  }

  @Test
  void should_throwHttp409_when_source_and_targe_namespaces_sor() {
    assertThatThrownBy(() ->
        service.copyRecordReferences(getCopyRecordReferencesModel(""),
            COLLABORATION_DIRECTIVE_WITHOUT_ID))
        .isInstanceOf(AppException.class)
        .satisfies(e -> {
          AppException appException = (AppException) e;
          assertEquals(409, appException.getError().getCode());
          assertEquals("Can't copy from SOR to SOR", appException.getError().getReason());
          assertEquals(
              "Source and target id is absent. You cant copy from System of Record to System of Record.",
              appException.getError().getMessage());
        });
  }

  private CopyRecordReferencesModel getCopyRecordReferencesModel(String target) {
    RecordVersionModel recordVersionModel = RecordVersionModel.builder().id(ID).build();
    return CopyRecordReferencesModel.builder()
        .target(target).records(List.of(recordVersionModel)).build();
  }

  private Optional<CollaborationContext> getCollaborationContext() {
    Map<String, String> collaborationProperties = CollaborationContextUtil.getCollaborationDirectiveProperties(
        COLLABORATION_DIRECTIVE_WITH_ID);
    return Optional.of(
        new CollaborationContext(UUID.fromString((String) collaborationProperties.get("id")),
            (String) collaborationProperties.get("application"), collaborationProperties));
  }

  private MultiRecordInfo getMultiRecordInfoWithInvalidRecords() {
    MultiRecordInfo response = new MultiRecordInfo();
    response.setInvalidRecords(List.of("invalid record"));
    return response;
  }

  private MultiRecordInfo getMultiRecordInfoWithRecords() {
    MultiRecordInfo response = new MultiRecordInfo();
    response.setRecords(List.of(new Record()));
    return response;
  }

  private MultiRecordIds getRecordIds(CopyRecordReferencesModel recordReferences) {
    return new MultiRecordIds(
        recordReferences.getRecords().stream().map(RecordVersionModel::getId)
            .toList(), new String[]{});
  }
}
