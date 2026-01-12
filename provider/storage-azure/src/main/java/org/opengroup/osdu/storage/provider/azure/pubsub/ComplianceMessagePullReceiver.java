// Copyright © Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.provider.azure.pubsub;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceMessagePushReceiver;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.opengroup.osdu.core.common.model.legal.jobs.ILegalComplianceChangeService;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class ComplianceMessagePullReceiver extends ComplianceMessagePushReceiver {
    @Autowired
    private ILegalComplianceChangeService legalComplianceChangeService;

    public void receiveMessage(LegalTagChangedCollection legalTagChanged, DpsHeaders headers) throws ComplianceUpdateStoppedException {
        this.legalComplianceChangeService.updateComplianceOnRecords(legalTagChanged, headers);
    }
}
