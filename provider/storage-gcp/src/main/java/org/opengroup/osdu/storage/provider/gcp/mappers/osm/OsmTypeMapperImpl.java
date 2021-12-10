/*
  Copyright 2021 Google LLC
  Copyright 2021 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package org.opengroup.osdu.storage.provider.gcp.mappers.osm;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.Key;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.gcp.osm.persistence.IdentityTranslator;
import org.opengroup.osdu.core.gcp.osm.translate.Instrumentation;
import org.opengroup.osdu.core.gcp.osm.translate.TypeMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * All Entity classes used in translation should be registered here even if don't need custom settings.
 * Each class is represented as Instrumentation object. At least class objects and rules for Identity translation should be provided.oi
 */
@Component
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "osmDriver")
public class OsmTypeMapperImpl extends TypeMapper {

    public OsmTypeMapperImpl() {
        super(Arrays.asList(
                //RecordMetadata: needs for two fields names and types custom settings. Id column is "id".
                new Instrumentation<>(RecordMetadata.class,
                        new HashMap<String, String>() {{
                            put("user", "createUser");
                            put("gcsVersionPaths", "bucket");
                        }},
                        new HashMap<String, Class<?>>() {{
                            put("createTime", Timestamp.class);
                            put("modifyTime", Timestamp.class);
                        }},
                        new IdentityTranslator<>(
                                RecordMetadata::getId,
                                (r, o) -> r.setId(((Key) o).getName())
                        ),
                        Collections.singletonList("id")
                ),
                //Schema: needs for one field name and type custom settings. Id column is "kind".
                new Instrumentation<>(Schema.class,
                        new HashMap<String, String>() {{
                            put("ext", "extension");
                        }},
                        new HashMap<String, Class<?>>() {{
                            put("schema", Blob.class);
                            put("ext", Blob.class);
                        }},
                        new IdentityTranslator<>(
                                Schema::getKind,
                                (r, o) -> r.setKind(((Key) o).getName())
                        ),
                        Collections.singletonList("kind")
                )
        ));
    }


}