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

package org.opengroup.osdu.storage.provider.aws.di;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.invoke.LambdaFunctionException;
import com.amazonaws.services.lambda.invoke.LambdaSerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.common.model.entitlements.*;
import org.opengroup.osdu.core.aws.entitlements.*;
import org.opengroup.osdu.core.common.model.entitlements.MemberInfo;
import org.opengroup.osdu.core.common.model.entitlements.Members;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.springframework.http.HttpStatus;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class EntitlementsServiceImpl implements IEntitlementsService {
    private DpsHeaders dpsHeaders;
    private EntitlementsServiceHelper entitlementsServiceHelper;

    private final static String ACCESS_DENIED = "Access denied";
    private final static String ACCESS_DENIED_MSG = "The user is not authorized to perform this action";

    public EntitlementsServiceImpl(DpsHeaders headers){
        this.dpsHeaders = headers;
    }

    public void setEntitlementsServiceHelper(String getGroupsFunctionName){
        entitlementsServiceHelper = new EntitlementsServiceHelper(Regions.US_EAST_1, getGroupsFunctionName);
    }

    @Override
    public MemberInfo addMember(GroupEmail groupEmail, MemberInfo memberInfo) throws EntitlementsException {
        // not implemented anywhere in storage
        throw new NotImplementedException();
    }

    @Override
    public Members getMembers(GroupEmail groupEmail, GetMembers getMembers) throws EntitlementsException {
        // not implemented anywhere in storage
        throw new NotImplementedException();
    }

    @Override
    public Groups getGroups() throws EntitlementsException {
        Groups groups;
        GroupsRequest request = entitlementsServiceHelper.constructRequest(this.dpsHeaders.getHeaders());

        try{
            GroupsResult groupsResult = entitlementsServiceHelper.getGroups(request);
            groups = getGroupsFromResult(groupsResult);
        } catch (JsonProcessingException e) {
            throw new EntitlementsException(e.getMessage(), new HttpResponse());
        } catch (LambdaFunctionException e){
            throw new EntitlementsException(e.getMessage(), new HttpResponse());
        } catch (LambdaSerializationException e){
            throw new EntitlementsException(e.getMessage(), new HttpResponse());
        } catch (IOException e){
            throw new EntitlementsException(e.getMessage(), new HttpResponse());
        }

        return groups;
    }

    @Override
    public GroupInfo createGroup(CreateGroup createGroup) throws EntitlementsException {
        // not implemented anywhere in storage
        throw new NotImplementedException();
    }

    @Override
    public void deleteMember(String s, String s1) throws EntitlementsException {
        // not implemented anywhere in storage
        throw new NotImplementedException();
    }

    @Override
    public Groups authorizeAny(String... strings) throws EntitlementsException {
        // not implemented anywhere in storage
        throw new NotImplementedException();
    }

    @Override
    public void authenticate() throws EntitlementsException {
        // not implemented anywhere in storage
        throw new NotImplementedException();
    }

    private Groups getGroupsFromResult(GroupsResult result) throws EntitlementsException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        Groups groups = new Groups();
        if(result.statusCode == HttpStatus.OK.value()) {
            TypeReference<List<GroupInfoRaw>> mapType = new TypeReference<List<GroupInfoRaw>>() {};
            List<GroupInfoRaw> groupInfosRaw = mapper.readValue(result.body, mapType);
            List<GroupInfo> groupInfos = new ArrayList<>();
            for(GroupInfoRaw groupInfoRaw : groupInfosRaw){
                GroupInfo groupInfo = new GroupInfo();
                groupInfo.setDescription(groupInfoRaw.groupDescription);
                groupInfo.setEmail(groupInfoRaw.groupEmail);
                groupInfo.setName(groupInfoRaw.groupName);
                groupInfos.add(groupInfo);
            }
            groups.setDesId(result.headers.get(RequestKeys.USER_HEADER_KEY));
            groups.setMemberEmail(result.headers.get(RequestKeys.USER_HEADER_KEY));
            groups.setGroups(groupInfos);
        } else {
            if(result.statusCode == HttpStatus.UNAUTHORIZED.value()){
                throw new AppException(HttpStatus.FORBIDDEN.value(), ACCESS_DENIED, ACCESS_DENIED_MSG);
            } else {
                throw new EntitlementsException(String.format("Getting groups for user returned %s status code",
                        result.statusCode), new HttpResponse());
            }
        }
        return groups;
    }
}
