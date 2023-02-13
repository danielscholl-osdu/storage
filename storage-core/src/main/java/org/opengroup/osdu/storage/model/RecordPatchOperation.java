package org.opengroup.osdu.storage.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.storage.swagger.SwaggerDoc;
import org.opengroup.osdu.storage.validation.api.ValidPatchOperation;
import org.opengroup.osdu.storage.validation.api.ValidPatchOperationPath;

import javax.validation.constraints.NotEmpty;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ValidPatchOperation
public class RecordPatchOperation {
    @ApiModelProperty(value = SwaggerDoc.PATCH_RECORD_OP,
            required = true,
            example = SwaggerDoc.PATCH_RECORD_OP_EXAMPLE)
    private String op;

    @ApiModelProperty(value = SwaggerDoc.PATCH_RECORD_PATH,
            required = true,
            example = SwaggerDoc.PATCH_RECORD_PATH_EXAMPLE)
    @ValidPatchOperationPath
    private String path;

    @ApiModelProperty(value = SwaggerDoc.PATCH_RECORD_PATH,
            required = true)
    @NotEmpty
    private String[] value;
}
