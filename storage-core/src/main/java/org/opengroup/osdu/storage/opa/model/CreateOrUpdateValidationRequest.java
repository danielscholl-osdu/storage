package org.opengroup.osdu.storage.opa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrUpdateValidationRequest {
    private CreateOrUpdateValidationInput input;
}
