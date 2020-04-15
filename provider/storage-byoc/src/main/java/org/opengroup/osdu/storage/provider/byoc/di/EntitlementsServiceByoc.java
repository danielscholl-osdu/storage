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

package org.opengroup.osdu.storage.provider.byoc.di;

import org.opengroup.osdu.core.common.model.entitlements.*;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
        List<GroupInfo> giList = new ArrayList();
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
