/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.messaging.jobs;

import com.google.gson.Gson;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagConsistencyValidator;

import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadDpsHeaders;

@Slf4j
@RequiredArgsConstructor
public class LegalTagChangedProcessing {

    private final LegalTagConsistencyValidator legalTagConsistencyValidator;
    private final LegalComplianceChangeServiceGcpImpl legalComplianceChangeServiceGcp;
    private final ThreadDpsHeaders dpsHeaders;

    public void process(OqmMessage oqmMessage) throws ComplianceUpdateStoppedException {
        String pubsubMessage = oqmMessage.getData();
        LegalTagChangedCollection dto = (new Gson()).fromJson(pubsubMessage, LegalTagChangedCollection.class);

        LegalTagChangedCollection validDto = this.legalTagConsistencyValidator.checkLegalTagStatusWithLegalService(dto);
        log.debug("LegalTags changed status validation via Legal service: {}.", validDto);
        Map<String, LegalCompliance> stringLegalComplianceMap = this.legalComplianceChangeServiceGcp.updateComplianceOnRecords(validDto, dpsHeaders);
        log.debug("Updated compliance on records: {}.", stringLegalComplianceMap);

    }
}
