// Copyright 2017-2021, Schlumberger
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

package org.opengroup.osdu.storage.util;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@Component
public class RecordUtilImpl implements RecordUtil {

  private static final String PATCH_OPERATION_ADD = "add";
  private static final String PATCH_OPERATION_REPLACE = "replace";
  private static final String PATCH_OPERATION_REMOVE = "remove";

  private final TenantInfo tenant;
  private final Gson gson;

  public RecordUtilImpl(TenantInfo tenant, Gson gson) {
    this.tenant = tenant;
    this.gson = gson;
  }

  @Override
  public void validateRecordIds(List<String> recordIds) {
    for (String id : recordIds) {
      if (!Record.isRecordIdValidFormatAndTenant(id, this.tenant.getName())) {
        String msg = String.format(
            "The record '%s' does not follow the naming convention: the first id component must be '%s'",
            id, this.tenant.getName());
        throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid record id", msg);
      }
    }
  }

  @Override
  public Map<String, String> mapRecordsAndVersions(List<String> recordIds) {
    Map<String, String> idMap = new HashMap<>();
    for (String id : recordIds) {
      String[] idParts = id.split(":");
      if (idParts.length == 4) {
        String idWithoutVersion = id.substring(0, id.length() - idParts[3].length() - 1);
        idMap.put(idWithoutVersion, id);
      } else {
        idMap.put(id, id);
      }
    }
    return idMap;
  }

  @Override
  public RecordMetadata updateRecordMetaDataForPatchOperations(RecordMetadata recordMetadata, List<PatchOperation> ops,
      String user, long timestamp) {
    List<PatchOperation> tagOperation = ops.stream()
        .filter(operation -> operation.getPath().startsWith("/tags"))
        .collect(toList());
    updateRecordMetaDataForTags(recordMetadata, tagOperation);

    List<PatchOperation> nonTagsOperation = new ArrayList<>(ops);
    nonTagsOperation.removeAll(tagOperation);

    if (!nonTagsOperation.isEmpty()) {
      recordMetadata = updateMetadataForNonTags(recordMetadata, nonTagsOperation);
    }

    recordMetadata.setModifyUser(user);
    recordMetadata.setModifyTime(timestamp);
    return recordMetadata;
  }

  private RecordMetadata updateMetadataForNonTags(RecordMetadata recordMetadata, List<PatchOperation> ops) {
    JsonObject metadata = this.gson.toJsonTree(recordMetadata).getAsJsonObject();

    for (PatchOperation op : ops) {
      String path = op.getPath();
      String[] pathComponents = path.split("/");

      JsonObject outer = metadata;
      JsonObject inner = metadata;

      for (int i = 1; i < pathComponents.length - 1; i++) {
        inner = outer.getAsJsonObject(pathComponents[i]);
        outer = inner;
      }

      JsonArray values = new JsonArray();
      for (String value : op.getValue()) {
        values.add(value);
      }
      inner.add(pathComponents[pathComponents.length - 1], values);
    }

    return gson.fromJson(metadata, RecordMetadata.class);
  }

  private void updateRecordMetaDataForTags(RecordMetadata recordMetadata, List<PatchOperation> ops) {
    for (PatchOperation operation : ops) {
      if (PATCH_OPERATION_ADD.equals(operation.getOp()) || PATCH_OPERATION_REPLACE.equals(operation.getOp())) {
        Map<String, String> newTags = Stream.of(operation.getValue())
            .map(value -> {
              String[] tagsPair = value.split(":");
              return new ImmutablePair<>(tagsPair[0], tagsPair[1]);
            })
            .collect(toMap(ImmutablePair::getKey, ImmutablePair::getValue));
        recordMetadata.getTags().putAll(newTags);
      } else if (PATCH_OPERATION_REMOVE.equals((operation.getOp()))) {
        Stream.of(operation.getValue())
            .forEach(recordMetadata.getTags()::remove);
      }
    }
  }
}