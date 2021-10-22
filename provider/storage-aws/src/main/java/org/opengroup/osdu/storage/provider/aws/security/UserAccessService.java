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

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.iam.IAMConfig;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.storage.provider.aws.cache.GroupCache;
import org.opengroup.osdu.storage.provider.aws.util.CacheHelper;
import org.opengroup.osdu.storage.service.IEntitlementsExtensionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UserAccessService {


    private CacheHelper cacheHelper;
    @Inject
    private GroupCache cache;
    @Inject
    private IEntitlementsFactory entitlementsFactory;
    @Inject
    private DpsHeaders dpsHeaders;
    @Inject
    private IEntitlementsExtensionService entitlementsExtensions;

    @Inject
    IServiceAccountJwtClient serviceAccountClient;
    private static final String ACCESS_DENIED_REASON = "Access denied";
    private static final String ACCESS_DENIED_MSG = "The user is not authorized to perform this action";

    private AWSCredentialsProvider amazonAWSCredentials;
    private AWSSimpleSystemsManagement ssmManager;
    @Value("${aws.region}")
    @Getter()
    @Setter(AccessLevel.PROTECTED)
    private String amazonRegion;
    @Value("${aws.environment}")
    @Getter()
    @Setter(AccessLevel.PROTECTED)
    private String environment;

    @PostConstruct
    public void init() {

        cacheHelper = new CacheHelper();

        amazonAWSCredentials = IAMConfig.amazonAWSCredentials();
        ssmManager = AWSSimpleSystemsManagementClientBuilder.standard()
                .withCredentials(amazonAWSCredentials)
                .withRegion(amazonRegion)
                .build();
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

    public void validateRecordAcl (RecordProcessing... records){
        //Records can be written by a user using ANY existing valid ACL
        List<String> groupNames = this.getPartitionGroupsforServicePrincipal(dpsHeaders);

        for (RecordProcessing record : records) {
            for (String acl : Acl.flattenAcl(record.getRecordMetadata().getAcl())) {
                String groupName = acl.split("@")[0].toLowerCase();
                if (!groupNames.contains(groupName)) {
                    throw new AppException(
                            HttpStatus.SC_FORBIDDEN,
                            "Invalid ACL",
                            String.format("ACL has invalid Group %s", acl));
                }
            }
        }
    }

    private List<String> getPartitionGroupsforServicePrincipal(DpsHeaders headers)
    {
        DpsHeaders newHeaders = DpsHeaders.createFromMap(headers.getHeaders());
        newHeaders.put(DpsHeaders.AUTHORIZATION, serviceAccountClient.getIdToken(null));
        //TODO: Refactor this, use either from SSM or use Istio service account and stop using hard code.

        newHeaders.put(DpsHeaders.USER_ID, getSsmParameter("/osdu/"+environment+"/service-principal-user"));
        Groups groups = this.entitlementsExtensions.getGroups(newHeaders);
        return groups.getGroupNames();
    }

    private String getSsmParameter(String parameterKey) {
        GetParameterRequest paramRequest = (new GetParameterRequest()).withName(parameterKey).withWithDecryption(true);
        GetParameterResult paramResult = ssmManager.getParameter(paramRequest);
        return paramResult.getParameter().getValue();
    }


}
