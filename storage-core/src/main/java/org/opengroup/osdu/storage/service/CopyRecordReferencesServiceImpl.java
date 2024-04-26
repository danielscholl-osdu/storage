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

import static org.opengroup.osdu.core.common.util.CollaborationContextUtil.getCollaborationDirectiveProperties;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.MultiRecordIds;
import org.opengroup.osdu.core.common.model.storage.MultiRecordInfo;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.storage.model.CopyRecordReferencesModel;
import org.opengroup.osdu.storage.model.RecordVersionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class CopyRecordReferencesServiceImpl implements CopyRecordReferencesService {

  private static final String ID = "id";

  @Autowired
  private BatchService batchService;
  @Autowired
  private CollaborationContextFactory collaborationContextFactory;
  @Autowired
  private IngestionService ingestionService;

  @Override
  public CopyRecordReferencesModel copyRecordReferences(CopyRecordReferencesModel request,
      String collaborationDirectives) {
    Map<String, String> collaborationHeader = getCollaborationDirectiveProperties(
        collaborationDirectives);
    if (!collaborationHeader.containsKey(ID) && !StringUtils.hasLength(
        request.getTarget())) {
      throw new AppException(HttpStatus.SC_CONFLICT, "Can't copy from SOR to SOR",
          "Source and target id is absent. You cant copy from System of Record to System of Record.");
    }
    Optional<CollaborationContext> collaborationContextHeader = getCollaborationContext(
        collaborationHeader, collaborationDirectives);
    Optional<CollaborationContext> collaborationContextBody = getCollaborationContext(request);
    List<Record> records = getValidRecords(request, collaborationContextHeader,
        collaborationContextBody);
    ingestionService.createUpdateRecords(false, records, collaborationHeader.get("application"),
        collaborationContextBody);
    return request;
  }

  private List<Record> getValidRecords(CopyRecordReferencesModel request,
      Optional<CollaborationContext> collaborationContextHeader,
      Optional<CollaborationContext> collaborationContextBody) {
    MultiRecordInfo sourceRecordInfo = batchService.getMultipleRecords(getRecordIds(request),
        collaborationContextHeader);
    if (!CollectionUtils.isEmpty(sourceRecordInfo.getInvalidRecords())) {
      throw new AppException(HttpStatus.SC_NOT_FOUND, "Records not found",
          "Source records not found: " + sourceRecordInfo.getInvalidRecords().toString());
    }
    MultiRecordInfo targetRecordInfo = batchService.getMultipleRecords(getRecordIds(request),
        collaborationContextBody);
    if (!CollectionUtils.isEmpty(targetRecordInfo.getRecords())) {
      throw new AppException(HttpStatus.SC_CONFLICT, "Records already exists",
          "One or more references already exist in the target namespace: "
              + sourceRecordInfo.getRecords().toString());
    }
    return sourceRecordInfo.getRecords();
  }

  private Optional<CollaborationContext> getCollaborationContext(
      CopyRecordReferencesModel recordReferences) {
    if (StringUtils.hasLength(recordReferences.getTarget())) {
      String collaborationDirectives = String.format("id=%s,application=pws",
          recordReferences.getTarget());
      return collaborationContextFactory.create(collaborationDirectives);
    } else {
      return Optional.empty();
    }
  }

  private Optional<CollaborationContext> getCollaborationContext(
      Map<String, String> collaborationHeader, String collaborationDirectives) {
    if (collaborationHeader.containsKey(ID)) {
      return collaborationContextFactory.create(collaborationDirectives);
    } else {
      return Optional.empty();
    }
  }

  private MultiRecordIds getRecordIds(CopyRecordReferencesModel recordReferences) {
    return new MultiRecordIds(
        recordReferences.getRecords().stream().map(RecordVersionModel::getId)
            .toList(), new String[]{});
  }
}
