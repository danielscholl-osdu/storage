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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.storage.service.IEntitlementsExtensionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.inject.Inject;

@Service
@Qualifier("ServiceAccountJwtAwsClientImplSDKV2")
public class UserAccessService {


    public static final String RECORD_WRITING_ERROR_REASON = "Error on writing record";
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
     * Optimized method to check if user has access to record without comparing lists.
     * This approach checks each user group directly against the ACL.
     *
     * @param acl The access control list to check against
     * @return true if the user has access, false otherwise
     */
    public boolean userHasAccessToRecord(Acl acl) {
        // Get user's groups
        Groups groups = this.entitlementsExtensions.getGroups(dpsHeaders);
        
        // Convert ACL lists to a set for O(1) lookup
        Set<String> aclGroups = new HashSet<>();
        aclGroups.addAll(Arrays.asList(acl.getOwners()));
        aclGroups.addAll(Arrays.asList(acl.getViewers()));
        
        // Check each user group directly against the ACL set
        for (GroupInfo group : groups.getGroups()) {
            if (aclGroups.contains(group.getEmail())) {
                return true; // Found a match, user has access
            }
        }
        
        return false;
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
                    HttpStatus.SC_BAD_REQUEST,
                    RECORD_WRITING_ERROR_REASON,
                    String.format("Could not find group \"%s\".", aclException.getAcl()));
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
