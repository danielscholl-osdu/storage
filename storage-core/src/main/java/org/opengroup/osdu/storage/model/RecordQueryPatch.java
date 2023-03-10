package org.opengroup.osdu.storage.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.storage.SwaggerDoc;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecordQueryPatch {
    @ApiModelProperty(value = SwaggerDoc.FETCH_RECORD_ID_LIST,
            required = true,
            example = SwaggerDoc.RECORD_ID_EXAMPLE)
    private List<String> ids;
}
