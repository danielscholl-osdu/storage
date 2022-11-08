package org.opengroup.osdu.storage.jobs;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;

import java.util.Map;
import java.util.Optional;

public interface ILegalComplianceChangeService {
    Map<String, LegalCompliance> updateComplianceOnRecords(LegalTagChangedCollection legalTagsChanged,
                                                           DpsHeaders headers, Optional<CollaborationContext> collaborationContext) throws ComplianceUpdateStoppedException;
}
