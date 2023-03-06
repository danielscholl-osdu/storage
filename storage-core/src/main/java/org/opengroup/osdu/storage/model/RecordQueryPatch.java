package org.opengroup.osdu.storage.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.storage.SwaggerDoc;
import org.opengroup.osdu.core.common.model.storage.validation.ValidNotNullCollection;
import org.opengroup.osdu.storage.validation.ValidationDoc;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecordQueryPatch {
    @ApiModelProperty(value = SwaggerDoc.FETCH_RECORD_ID_LIST,
            required = true,
            example = SwaggerDoc.RECORD_ID_EXAMPLE)
    @ValidNotNullCollection
    @NotEmpty(message = ValidationDoc.RECORD_ID_LIST_NOT_EMPTY)
    @Size(min = 1, max = 100, message = ValidationDoc.PATCH_RECORDS_MAX)
    private List<String> ids;
}
