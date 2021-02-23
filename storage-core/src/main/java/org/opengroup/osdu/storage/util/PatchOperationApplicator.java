// Copyright © Schlumberger
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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

import java.util.List;

public class PatchOperationApplicator {

    private final Gson gson = new Gson();

    public RecordMetadata updateMetadataWithOperations(RecordMetadata recordMetadata, List<PatchOperation> ops) {
        JsonObject metadata = this.gson.toJsonTree(recordMetadata).getAsJsonObject();

        for (PatchOperation op : ops) {
            String path = op.getPath();
            String[] pathComponents = path.split("/");

            JsonObject outter = metadata;
            JsonObject inner = metadata;

            for (int i = 1; i < pathComponents.length - 1; i++) {
                inner = outter.getAsJsonObject(pathComponents[i]);
                outter = inner;
            }

            JsonArray values = new JsonArray();
            for (String value : op.getValue()) {
                values.add(value);
            }
            inner.add(pathComponents[pathComponents.length - 1], values);
        }

        return gson.fromJson(metadata, RecordMetadata.class);
    }
}
