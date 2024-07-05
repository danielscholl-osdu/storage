// Copyright © 2020 Amazon Web Services
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

package org.opengroup.osdu.storage.provider.aws.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;


import org.apache.http.HttpStatus;

import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.storage.service.IEntitlementsExtensionService;
import org.springframework.stereotype.Service;

@Service
public class UserAccessService {


    @Inject
    private DpsHeaders dpsHeaders;
    @Inject
    private IEntitlementsExtensionService entitlementsExtensions;
    @Inject
    IServiceAccountJwtClient serviceAccountClient;

    private static final String SERVICE_PRINCIPAL_ID = "";

    private static class InvalidACLException extends Exception {
        private final String acl;
        public InvalidACLException(String acl) {
            this.acl = acl;
        }

        public String getAcl() {
            return acl;
        }
    }

    /**
     * Unideal way to check if user has access to record because a list is being compared
     * for a match in a list. Future improvements include redesigning our dynamo schema to
     * get around this and redesigning dynamo schema to stop parsing the acl out of
     * recordmetadata
     *
     * @param acl
     * @return
     */
    // Optimize entitlements record ACL design to not compare list against list
    public boolean userHasAccessToRecord(Acl acl) {
        Groups groups = this.entitlementsExtensions.getGroups(dpsHeaders);
        HashSet<String> allowedGroups = new HashSet<>();

        for (String owner : acl.getOwners()) {
            allowedGroups.add(owner);
        }

        for (String viewer : acl.getViewers()) {
            allowedGroups.add(viewer);
        }

        List<GroupInfo> memberGroups = groups.getGroups();
        HashSet<String> memberGroupsSet = new HashSet<>();

        for (GroupInfo memberGroup : memberGroups) {
            memberGroupsSet.add(memberGroup.getEmail());
        }

        return allowedGroups.stream().anyMatch(memberGroupsSet::contains);
    }

    private void validateRecordAclsForServicePrincipal(RecordProcessing... records) throws InvalidACLException {
        //Records can be written by a user using ANY existing valid ACL
        Set<String> groupNames = this.getPartitionGroupsforServicePrincipal(dpsHeaders);

        for (RecordProcessing recordProcessing : records) {
            for (String acl : Acl.flattenAcl(recordProcessing.getRecordMetadata().getAcl())) {
                String groupName = acl.split("@")[0].toLowerCase();
                if (!groupNames.contains(groupName)) {
                    throw new InvalidACLException(acl);
                }
            }
        }
    }

    public void validateRecordAcl (RecordProcessing... records){
        try {
            validateRecordAclsForServicePrincipal(records);
        } catch (InvalidACLException e) {
            // We are invaliding the groups of the service principal and rechecking in case the
            // group that a user specified in the record ACL was recently created. Not being
            // able to use a newly created group is considered blocking.
            this.entitlementsExtensions.invalidateGroups(getServicePrincipalHeaders(dpsHeaders));
            try {
                validateRecordAclsForServicePrincipal(records);
            } catch (InvalidACLException aclException) {
                throw new AppException(
                    HttpStatus.SC_FORBIDDEN,
                    "Invalid ACL",
                    String.format("ACL has invalid Group %s", aclException.getAcl()));
            }
        }
    }

    private DpsHeaders getServicePrincipalHeaders(DpsHeaders headers) {
        DpsHeaders newHeaders = DpsHeaders.createFromMap(headers.getHeaders());
        newHeaders.put(DpsHeaders.AUTHORIZATION, serviceAccountClient.getIdToken(null));
        //Refactor this, use either from SSM or use Istio service account and stop using hard code.

        newHeaders.put(DpsHeaders.USER_ID, SERVICE_PRINCIPAL_ID);
        return newHeaders;
    }

    private Set<String> getPartitionGroupsforServicePrincipal(DpsHeaders headers)
    {
        Groups groups = this.entitlementsExtensions.getGroups(getServicePrincipalHeaders(headers));
        return new HashSet<>(groups.getGroupNames());
    }

}
