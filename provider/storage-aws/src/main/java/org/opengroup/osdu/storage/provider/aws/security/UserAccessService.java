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

import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.service.IEntitlementsExtensionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.inject.Inject;

@Service
@Qualifier("ServiceAccountJwtAwsClientImplSDKV2")
public class UserAccessService {
    private static final String ROOT_USER_DATA_GROUP_NAME = "users.data.root";
    @Inject
    private DpsHeaders dpsHeaders;
    @Inject
    private IEntitlementsExtensionService entitlementsExtensions;



    private boolean isDataManager(Groups groups) {
        return groups.any(ROOT_USER_DATA_GROUP_NAME);
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
        
        if (groups == null) {
            return false;
        }

        if (isDataManager(groups)) {
            return true; // Data managers have access to all records
        }
        
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
}
