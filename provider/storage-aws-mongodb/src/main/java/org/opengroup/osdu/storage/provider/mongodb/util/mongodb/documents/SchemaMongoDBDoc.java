package org.opengroup.osdu.storage.provider.mongodb.util.mongodb.documents;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.aws.mongodb.BasicMongoDBDoc;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.springframework.data.annotation.Id;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SchemaMongoDBDoc implements BasicMongoDBDoc {

    @Id
    private String kind;

    private String dataPartitionId;

    private String user;

    private List<SchemaItem> schemaItems;

    private Map<String, Object> extension;

    @Override
    public String getCursor() {
        return kind;
    }
}

