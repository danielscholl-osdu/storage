/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/
package org.opengroup.osdu.storage.provider.ibm;

import java.util.Set;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.service.EntitlementsAndCacheServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class EntitlementsAndCacheServiceIBM extends EntitlementsAndCacheServiceImpl {

    @Autowired
    private JaxRsDpsLog logger;

    public boolean hasAccessToData(DpsHeaders headers, Set<String> acls) {
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
        // 1. group domain is tenant.thisServiceOperatingDomain; check if all acls matches this group
        String domain = email.split("@")[1];
        for (String acl : acls) {
            String[] aclParts = acl.split("@");
            if (!aclParts[1].equalsIgnoreCase(domain)) {
                return false;
            }
        }

        // 2. check if user has a role that matches one of the specified acls
        for (String acl : acls) {
            String[] aclParts = acl.split("@");
            for (GroupInfo gi : groups.getGroups()) {
                if (aclParts[0].equalsIgnoreCase(gi.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
