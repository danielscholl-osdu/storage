// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.storage.service;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EntitlementsAndCacheServiceImpl implements IEntitlementsExtensionService {

    private static final String ERROR_REASON = "Access denied";
    private static final String ERROR_MSG = "The user is not authorized to perform this action";

    @Autowired
    private IEntitlementsFactory factory;

    @Autowired
    private ICache<String, Groups> cache;

    @Autowired
    private JaxRsDpsLog logger;

    @Override
    public String authorize(DpsHeaders headers, String... roles) {
        Groups groups = this.getGroups(headers);
        if (groups.any(roles)) {
            return groups.getDesId();
        } else {
            throw new AppException(HttpStatus.SC_FORBIDDEN, ERROR_REASON, ERROR_MSG);
        }
    }

    @Override
    public boolean isValidAcl(DpsHeaders headers, Set<String> acls) {
        Groups groups = this.getGroups(headers);
        if (groups.getGroups() == null || groups.getGroups().isEmpty()) {
            this.logger.error("Error on getting groups for user: " + headers.getUserEmail());
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unknown error",
                    "Unknown error happened when validating ACL");
        }
        String email = groups.getGroups().get(0).getEmail();
        if (!email.matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")) {
            this.logger.error("Email address is invalid for this group: " + groups.getGroups().get(0));
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unknown error",
                    "Unknown error happened when validating ACL");
        }
        String domain = email.split("@")[1];
        for (String acl : acls) {
            if (!acl.split("@")[1].equalsIgnoreCase(domain)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasOwnerAccess(DpsHeaders headers, String[] ownerList) {
        Groups groups = this.getGroups(headers);
        Set<String> aclList = new HashSet<>();

        for (String owner : ownerList) {
            aclList.add(owner.split("@")[0]);
        }

        String[] acls = new String[aclList.size()];
        return groups.any(aclList.toArray(acls));
    }

    @Override
    public List<RecordMetadata> hasValidAccess(List<RecordMetadata> recordsMetadata, DpsHeaders headers) {
        Groups groups = this.getGroups(headers);
        List<RecordMetadata> result = new ArrayList<>();

        for (RecordMetadata recordMetadata : recordsMetadata) {
            Acl storageAcl = recordMetadata.getAcl();
            if (hasAccess(storageAcl, groups)) {
                result.add(recordMetadata);
            } else {
                this.logger.warning("Post ACL check fails: " + recordMetadata.getId());
            }
        }

        return result;
    }

    private boolean hasAccess(Acl storageAcl, Groups groups) {
        String[] viewers = storageAcl.getViewers();
        String[] owners = storageAcl.getOwners();
        Set<String> aclList = new HashSet<>();

        for (String viewer : viewers) {
            aclList.add(viewer.split("@")[0]);
        }
        for (String owner : owners) {
            aclList.add(owner.split("@")[0]);
        }

        String[] acls = new String[aclList.size()];
        if (groups.any(aclList.toArray(acls))) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Groups getGroups(DpsHeaders headers) {
        String cacheKey = this.getGroupCacheKey(headers);
        Groups groups = this.cache.get(cacheKey);

        if (groups == null) {
            IEntitlementsService service = this.factory.create(headers);
            try {
                groups = service.getGroups();
                this.cache.put(cacheKey, groups);
                this.logger.info("Entitlements cache miss");

            } catch (EntitlementsException e) {
                e.printStackTrace();
                HttpResponse response = e.getHttpResponse();
                this.logger.error(String.format("Error requesting entitlements service %s", response));
                throw new AppException(e.getHttpResponse().getResponseCode(), ERROR_REASON, ERROR_MSG, e);
            }
        }

        return groups;
    }

    protected static String getGroupCacheKey(DpsHeaders headers) {
        String key = String.format("entitlement-groups:%s:%s", headers.getPartitionIdWithFallbackToAccountId(),
                headers.getAuthorization());
        return Crc32c.hashToBase64EncodedString(key);
    }
}
