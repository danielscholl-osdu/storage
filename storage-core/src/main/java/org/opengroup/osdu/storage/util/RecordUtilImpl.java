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

import java.util.*;
import java.util.stream.Stream;

import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.springframework.stereotype.Component;

@Component
public class RecordUtilImpl implements RecordUtil {

    private static final String PATCH_OPERATION_ADD = "add";
    private static final String PATCH_OPERATION_REPLACE = "replace";
    private static final String PATCH_OPERATION_REMOVE = "remove";
    private static final String LEGAL = "legal";
    private static final String LEGAL_TAGS = "legaltags";
    private static final String ACL = "acl";
    private static final String VIEWERS = "viewers";
    private static final String OWNERS = "owners";
    private static final String ERROR_MSG = "Cannot delete";
    private static final String ERROR_REASON = "Cannot remove all ";

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
        if (tagOperation.size() > 0) {
            updateRecordMetaDataForTags(recordMetadata, tagOperation);
        }

        List<PatchOperation> legalOperation = ops.stream()
                .filter(operation -> operation.getPath().startsWith("/legal"))
                .collect(toList());
        if (legalOperation.size() > 0) {
            recordMetadata = updateMetadataForAclAndLegal(recordMetadata, legalOperation);
        }

        List<PatchOperation> aclOperation = ops.stream()
                .filter(operation -> operation.getPath().startsWith("/acl"))
                .collect(toList());
        if (aclOperation.size() > 0) {
            recordMetadata = updateMetadataForAclAndLegal(recordMetadata, aclOperation);
        }

        recordMetadata.setModifyUser(user);
        recordMetadata.setModifyTime(timestamp);
        return recordMetadata;
    }

    public boolean hasVersionPath(List<String> gcsVersionPaths, Long version) {
        return gcsVersionPaths.stream()
                .filter(Objects::nonNull)
                .anyMatch(path -> path.endsWith("/" + version));
    }

    private RecordMetadata updateMetadataForAclAndLegal(RecordMetadata recordMetadata, List<PatchOperation> ops) {
        JsonObject metadata = this.gson.toJsonTree(recordMetadata).getAsJsonObject();
        String errorPath = "";

        for (PatchOperation op : ops) {
            String path = op.getPath();
            String[] pathComponents = path.split("/");

            JsonObject outer = metadata;
            JsonObject inner = metadata;

            Set<String> oldValues = new HashSet<String>();

            for (int i = 1; i < pathComponents.length - 1; i++) {
                inner = outer.getAsJsonObject(pathComponents[i]);
                outer = inner;
                errorPath = getExistingValuesAndErrorPath(pathComponents, i, oldValues, recordMetadata);

            }
            JsonArray values = new JsonArray();

            Set<String> newValues = new HashSet<>();
            newValues.addAll(Arrays.asList(op.getValue()));

            for (String value : op.getValue()) {
                values.add(value);
            }
            executeCorrespondingOperation(op, pathComponents, outer, inner, values, oldValues, newValues, errorPath);
        }
        return gson.fromJson(metadata, RecordMetadata.class);
    }

    private String getExistingValuesAndErrorPath(String[] pathComponents, int i, Set<String> oldValues, RecordMetadata recordMetadata) {
        switch (pathComponents[i].toLowerCase()) {
            case LEGAL:
                if (LEGAL_TAGS.equalsIgnoreCase(pathComponents[pathComponents.length - 1])) {
                    oldValues.addAll(recordMetadata.getLegal().getLegaltags());
                    return LEGAL_TAGS;
                }
                break;
            case ACL:
                switch (pathComponents[pathComponents.length - 1].toLowerCase()) {
                    case VIEWERS:
                        oldValues.addAll(Arrays.asList(recordMetadata.getAcl().getViewers()));
                        return ACL + " " + VIEWERS;
                    case OWNERS:
                        oldValues.addAll(Arrays.asList(recordMetadata.getAcl().getOwners()));
                        return ACL + " " + OWNERS;
                }
        }
        return "";
    }

    private void executeCorrespondingOperation(PatchOperation op, String[] pathComponents, JsonObject outer,
                                               JsonObject inner, JsonArray values, Set<String> oldValues,
                                               Set<String> newValues, String errorPath) {
        switch (op.getOp().toLowerCase()) {
            case PATCH_OPERATION_ADD:
                setOriginalAclAndLegal(pathComponents, outer, values);
                values = removeDuplicates(values);
                inner.add(pathComponents[pathComponents.length - 1], values);
                break;
            case PATCH_OPERATION_REPLACE:
                inner.add(pathComponents[pathComponents.length - 1], values);
                break;
            case PATCH_OPERATION_REMOVE:
                patchRemoveForAclAndLegal(oldValues, newValues, errorPath);
                JsonArray jsonArray = new JsonArray();
                for (String stringValue : oldValues) {
                    jsonArray.add(stringValue);
                }
                inner.add(pathComponents[pathComponents.length - 1], jsonArray);
                break;
        }

    }


    private void patchRemoveForAclAndLegal(Set<String> oldValues, Set<String> newValues, String errorPath) {
        //prevent from removing all acl viewers, acl owners or legaltags
        oldValues.removeAll(newValues);
        if (oldValues.isEmpty()) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, ERROR_REASON + errorPath, ERROR_MSG);
        }
    }

    private JsonArray removeDuplicates(JsonArray values) {
        Set<String> items = new LinkedHashSet<>();
        for (JsonElement value : values) {
            if (!(value.isJsonNull())) {
                items.add(value.getAsString());
            }
        }
        values = new JsonParser().parse(new Gson().toJson(items)).getAsJsonArray();
        return values;
    }


    private void setOriginalAclAndLegal(String[] pathComponents, JsonObject outer, JsonArray values) {
        if (pathComponents[1].equalsIgnoreCase(ACL)) {
            if (pathComponents[2].equalsIgnoreCase(VIEWERS)) {
                values.addAll(gson.fromJson(outer.get(VIEWERS), JsonArray.class));
            } else if (pathComponents[2].equalsIgnoreCase(OWNERS)) {
                values.addAll(gson.fromJson(outer.get(OWNERS), JsonArray.class));
            }
        } else if (pathComponents[1].equalsIgnoreCase(LEGAL)) {
            values.addAll(gson.fromJson(outer.get(LEGAL_TAGS), JsonArray.class));
        }
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