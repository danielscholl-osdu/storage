// Copyright 2017-2023, Schlumberger
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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static org.opengroup.osdu.storage.util.RecordConstants.DATA_PATH;
import static org.opengroup.osdu.storage.util.RecordConstants.META_PATH;
import static org.opengroup.osdu.storage.util.RecordConstants.PATH;

public class JsonPatchUtil {

    private final static Logger LOGGER = LoggerFactory.getLogger(JsonPatchUtil.class);
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static <T> T applyPatch(JsonPatch jsonPatch, Class<T> targetClass, T target) {
        try {
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
            objectMapper.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE);
            objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            JsonNode patched = jsonPatch.apply(objectMapper.convertValue(target, JsonNode.class));
            return objectMapper.treeToValue(patched, targetClass);
        } catch (JsonPatchException e) {
            LOGGER.error("JsonPatchException during patch operation", e);
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Bad input for JsonPatch operation", "JsonPatchException during patch operation", e);
        } catch (JsonProcessingException e) {
            LOGGER.error("JsonProcessingException during patch operation", e);
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Bad input for JsonPatch operation", "JsonProcessingException during patch operation", e);
        }
    }

    public static boolean isDataOrMetaBeingUpdated (JsonPatch jsonPatch) {
        JsonNode patchNode = objectMapper.convertValue(jsonPatch, JsonNode.class);
        Iterator<JsonNode> nodes = patchNode.elements();
        while (nodes.hasNext()) {
            JsonNode currentNode = nodes.next();
            if (currentNode.findPath(PATH).textValue().startsWith(DATA_PATH) || currentNode.findPath(PATH).textValue().startsWith(META_PATH))
                return true;
        }
        return false;
    }

    public static JsonPatch getJsonPatchForRecord(RecordMetadata recordMetadata, JsonPatch inputJsonPatch) throws IOException {
        ArrayNode resultNode = objectMapper.createArrayNode();
        List<JsonNode> patchOperations = StreamSupport.stream(objectMapper.convertValue(inputJsonPatch, JsonNode.class).spliterator(), false)
                .distinct().collect(toList());
        for (JsonNode currentNode : patchOperations) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode(1);
            arrayNode.add(currentNode);
            JsonPatch currentPatch = JsonPatch.fromJson(arrayNode);
            if (!hasDuplicateAcl(JsonPatchUtil.applyPatch(currentPatch, RecordMetadata.class, recordMetadata).getAcl())) {
                resultNode.add(currentNode);
            }
        }
        return JsonPatch.fromJson(resultNode);
    }

    private static boolean hasDuplicateAcl(Acl acl) {
        Set<String> viewers = new HashSet<>();
        Set<String> owners = new HashSet<>();
        for (String viewer : acl.getViewers()) {
            if (viewers.contains(viewer))
                return true;
            else
                viewers.add(viewer);
        }
        for (String owner : acl.getOwners()) {
            if (owners.contains(owner))
                return true;
            else
                owners.add(owner);
        }
        return false;
    }

    public static boolean isEmptyAclOrLegal(RecordMetadata recordMetadata) {
        return recordMetadata.getAcl() == null ||
                recordMetadata.getAcl().getViewers() == null ||
                recordMetadata.getAcl().getOwners() == null ||
                recordMetadata.getLegal() == null ||
                recordMetadata.getAcl().getOwners().length == 0 ||
                recordMetadata.getAcl().getViewers().length == 0 ||
                CollectionUtils.isEmpty(recordMetadata.getLegal().getLegaltags());
    }

    public static boolean isEmptyAclOrLegal(Record record) {
        return record.getAcl() == null ||
                record.getAcl().getViewers() == null ||
                record.getAcl().getOwners() == null ||
                record.getLegal() == null ||
                record.getAcl().getOwners().length == 0 ||
                record.getAcl().getViewers().length == 0 ||
                CollectionUtils.isEmpty(record.getLegal().getLegaltags());
    }

    public static boolean isKindBeingUpdated(JsonPatch jsonPatch) {
        JsonNode patchNode = objectMapper.convertValue(jsonPatch, JsonNode.class);
        Iterator<JsonNode> nodes = patchNode.elements();
        while (nodes.hasNext()) {
            JsonNode currentNode = nodes.next();
            if (currentNode.findPath("path").toString().contains("kind"))
                return true;
        }
        return false;
    }

    public static String getNewKindFromPatchInput(JsonPatch jsonPatch) {
        JsonNode patchNode = objectMapper.convertValue(jsonPatch, JsonNode.class);
        Iterator<JsonNode> nodes = patchNode.elements();
        while (nodes.hasNext()) {
            JsonNode currentNode = nodes.next();
            if (currentNode.findPath("path").toString().contains("kind")) {
                return currentNode.findPath("value").textValue();
            }
        }
        throw new RuntimeException("Failed to retrieve kind value from jsonpatch: " + jsonPatch.toString());
    }
}
