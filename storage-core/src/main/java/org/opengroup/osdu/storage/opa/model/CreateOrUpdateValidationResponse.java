package org.opengroup.osdu.storage.opa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrUpdateValidationResponse {
    private List<ValidationOutputRecord> result;
}
