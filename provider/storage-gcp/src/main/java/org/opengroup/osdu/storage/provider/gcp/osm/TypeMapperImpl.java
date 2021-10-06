package org.opengroup.osdu.storage.provider.gcp.osm;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.Key;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.gcp.osm.persistence.Identity;
import org.opengroup.osdu.core.gcp.osm.translate.Instrumentation;
import org.opengroup.osdu.core.gcp.osm.translate.TypeMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

@Component
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "osmDriver")
public class TypeMapperImpl extends TypeMapper {

    public TypeMapperImpl() {
        super(Arrays.asList(
                new Instrumentation<>(RecordMetadata.class,
                        new HashMap<String, String>() {{
                            put("user", "createUser");
                            put("gcsVersionPaths", "bucket");
                        }},
                        new HashMap<String, Class<?>>() {{
                            put("createTime", Timestamp.class);
                            put("modifyTime", Timestamp.class);
                        }},
                        new Identity<>(
                                RecordMetadata::getId,
                                (r, o) -> r.setId(((Key) o).getName())
                        ),
                        Collections.singletonList("id")
                ),
                new Instrumentation<>(Schema.class,
                        new HashMap<String, String>() {{
                            put("ext", "extension");
                        }},
                        new HashMap<String, Class<?>>() {{
                            put("schema", Blob.class);
                            put("ext", Blob.class);
                        }},
                        new Identity<>(
                                Schema::getKind,
                                (r, o) -> r.setKind(((Key) o).getName())
                        ),
                        Collections.singletonList("kind")
                )
        ));
    }


}