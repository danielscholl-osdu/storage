package org.opengroup.osdu.storage.jobs;

import com.google.gson.Gson;
import org.opengroup.osdu.core.common.http.RequestBodyExtractor;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagConsistencyValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

@Component
@RequestScope
public class ComplianceMessagePushReceiver {

    @Autowired
    private DpsHeaders dpsHeaders;

    @Autowired
    private RequestBodyExtractor requestBodyExtractor;

    @Autowired
    private LegalTagConsistencyValidator legalTagConsistencyValidator;

    @Autowired
    private ILegalComplianceChangeService legalComplianceChangeService;

    public void receiveMessageFromHttpRequest(Optional<CollaborationContext> collaborationContext) throws ComplianceUpdateStoppedException {
        LegalTagChangedCollection dto = new Gson().fromJson(this.requestBodyExtractor.extractDataFromRequestBody(),
                LegalTagChangedCollection.class);
        LegalTagChangedCollection validDto = this.legalTagConsistencyValidator.checkLegalTagStatusWithLegalService(dto);
        this.legalComplianceChangeService.updateComplianceOnRecords(validDto, this.dpsHeaders, collaborationContext);
    }
}
