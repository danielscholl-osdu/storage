// Copyright © Amazon Web Services
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

import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.storage.provider.aws.util.CacheHelper;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;

@Service
public class UserAccessService {

    private CacheHelper cacheHelper;
    @Inject
    private ICache<String, Groups> cache;
    @Inject
    private IEntitlementsFactory entitlementsFactory;
    @Inject
    private DpsHeaders dpsHeaders;

    private static final String ACCESS_DENIED_REASON = "Access denied";
    private static final String ACCESS_DENIED_MSG = "The user is not authorized to perform this action";

    @PostConstruct
    public void init() {
        cacheHelper = new CacheHelper();
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
    // TODO: Optimize entitlements record ACL design to not compare list against list
    public boolean userHasAccessToRecord(Acl acl) {
        Groups groups = getGroups();
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

    // TODO: duplicate logic resides in EntitlementsAndCacheServiceImpl
    private Groups getGroups() {
        String cacheKey = this.cacheHelper.getGroupCacheKey(this.dpsHeaders);
        Groups groups = this.cache.get(cacheKey);

        if (groups == null) {
            groups = refreshGroups();
        }

        return groups;
    }

    private Groups refreshGroups() {
        Groups groups;
        IEntitlementsService service = this.entitlementsFactory.create(this.dpsHeaders);

        try {
            groups = service.getGroups();
            this.cache.put(this.cacheHelper.getGroupCacheKey(this.dpsHeaders), groups);
        } catch (EntitlementsException e) {
            e.printStackTrace();
            throw new AppException(e.getHttpResponse().getResponseCode(), ACCESS_DENIED_REASON, ACCESS_DENIED_MSG, e);
        }

        return groups;
    }
}
