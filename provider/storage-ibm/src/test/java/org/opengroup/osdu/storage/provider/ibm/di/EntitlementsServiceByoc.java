/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.storage.provider.ibm.di;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.model.entitlements.CreateGroup;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GetMembers;
import org.opengroup.osdu.core.common.model.entitlements.GroupEmail;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.entitlements.MemberInfo;
import org.opengroup.osdu.core.common.model.entitlements.Members;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class EntitlementsServiceByoc implements IEntitlementsService {
    DpsHeaders headers;

    public EntitlementsServiceByoc(DpsHeaders headers){
        this.headers = headers;
    }

    @Override
    public MemberInfo addMember(GroupEmail groupEmail, MemberInfo memberInfo) throws EntitlementsException {
        return null;
    }

    @Override
    public Members getMembers(GroupEmail groupEmail, GetMembers getMembers) throws EntitlementsException {
        return null;
    }

    @Override
    public Groups getGroups() throws EntitlementsException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        List<GroupInfo> giList = new ArrayList<GroupInfo>();
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        for(GrantedAuthority authority : authorities)
        {
            GroupInfo gi = new GroupInfo();
            String role = authority.getAuthority();
            if (role.startsWith(StorageRole.PREFIX)){
                role = role.substring(StorageRole.PREFIX.length());
            }
            gi.setName(role);
            gi.setEmail(email);
            giList.add(gi);
        }
        if (giList.size() > 0)
        {
            Groups groups = new Groups();
            groups.setGroups(giList);
            groups.setDesId(email);
            return groups;
        }

        HttpResponse response = new HttpResponse();
        response.setResponseCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        throw new EntitlementsException("no authorities found", response);
    }

    @Override
    public GroupInfo createGroup(CreateGroup createGroup) throws EntitlementsException {
        return null;
    }

    @Override
    public void deleteMember(String s, String s1) throws EntitlementsException {

    }

    @Override
    public Groups authorizeAny(String... strings) throws EntitlementsException {
        return null;
    }

    @Override
    public void authenticate() throws EntitlementsException {

    }
}
