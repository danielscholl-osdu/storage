package org.opengroup.osdu.storage.model;

import com.github.fge.jsonpatch.JsonPatch;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.core.common.model.storage.SwaggerDoc;
import org.opengroup.osdu.core.common.model.storage.validation.ValidBulkQuery;
import org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidInputSize;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatchOperation;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatchPath;

import javax.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatchRecordsRequestModel {

    @ApiModelProperty(value = SwaggerDoc.RECORD_QUERY_CONDITION, required = true)
    @NotNull(message = ValidationDoc.RECORD_QUERY_CONDITION_NOT_EMPTY)
    @ValidBulkQuery
    @ValidInputSize
    private RecordQuery query;

    @ApiModelProperty(value = org.opengroup.osdu.storage.swagger.SwaggerDoc.PATCH_RECORD_OPERATIONS, required = true)
    @NotNull(message = org.opengroup.osdu.storage.validation.ValidationDoc.PATCH_RECORD_OPERATIONS_NOT_EMPTY)
    @ValidJsonPatchOperation(message = org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_PATCH_OPERATION)
    @ValidJsonPatchPath(message = org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_PATCH_PATH)
    private JsonPatch ops;
}
