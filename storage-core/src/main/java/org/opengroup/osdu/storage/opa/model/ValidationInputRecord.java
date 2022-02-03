package org.opengroup.osdu.storage.opa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationInputRecord {
    private String id;
    private String kind;
    private Legal legal;
    private Acl acls;
}
