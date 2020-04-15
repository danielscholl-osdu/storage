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

package org.opengroup.osdu.storage.provider.aws;

import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.provider.aws.util.CacheHelper;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.LegalTagAssociationDoc;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.util.*;

@Repository
public class RecordsMetadataRepositoryImpl implements IRecordsMetadataRepository<String> {

    @Value("${aws.dynamodb.table.prefix}")
    String tablePrefix;

    @Value("${aws.dynamodb.region}")
    String dynamoDbRegion;

    @Value("${aws.dynamodb.endpoint}")
    String dynamoDbEndpoint;

    @Inject
    private JaxRsDpsLog logger;

    private DynamoDBQueryHelper queryHelper;

    private CacheHelper cacheHelper;

    // below attributes needed for record acl checks
    @Inject
    private ICache<String, Groups> cache;

    @Inject
    private IEntitlementsFactory factory;

    @Inject
    private DpsHeaders headers;

    private final static String ACCESS_DENIED_REASON = "Access denied";
    private static final String ACCESS_DENIED_MSG = "The user is not authorized to perform this action";


    @PostConstruct
    public void init(){
        queryHelper = new DynamoDBQueryHelper(dynamoDbEndpoint, dynamoDbRegion, tablePrefix);
        cacheHelper = new CacheHelper();
    }

    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata) {
        if (recordsMetadata != null) {
            for (RecordMetadata recordMetadata : recordsMetadata) {
                // user should be part of the acl of the record being saved
                if (userHasAccessToRecord(getGroups(), recordMetadata.getAcl())) {
                    RecordMetadataDoc doc = new RecordMetadataDoc();

                    // Set the core fields (what is expected in every implementation)
                    doc.setId(recordMetadata.getId());
                    doc.setMetadata(recordMetadata);

                    // Add extra indexed fields for querying in DynamoDB
                    doc.setKind(recordMetadata.getKind());
                    doc.setLegaltags(recordMetadata.getLegal().getLegaltags());
                    doc.setStatus(recordMetadata.getStatus().name());
                    doc.setUser(recordMetadata.getUser());

                    // Store the record to the database
                    queryHelper.save(doc);
                    saveLegalTagAssociation(recordMetadata.getId(), recordMetadata.getLegal().getLegaltags());
                } else {
                    throw new AppException(HttpStatus.FORBIDDEN.value(), ACCESS_DENIED_REASON, ACCESS_DENIED_MSG);
                }
            }
        }
        return recordsMetadata;
    }

    @Override
    public void delete(String id) {
        RecordMetadata rmd = get(id); // needed for authorization check
        queryHelper.deleteByPrimaryKey(RecordMetadataDoc.class, id);
        for(String legalTag : rmd.getLegal().getLegaltags()){
            deleteLegalTagAssociation(id, legalTag);
        }
    }

    @Override
    public RecordMetadata get(String id) {
        RecordMetadataDoc doc = queryHelper.loadByPrimaryKey(RecordMetadataDoc.class, id);
        if (doc == null) {
            return null;
        } else {
            RecordMetadata rmd = doc.getMetadata();
            if (userHasAccessToRecord(getGroups(), rmd.getAcl())) {
                return doc.getMetadata();
            } else {
                throw new AppException(HttpStatus.FORBIDDEN.value(), ACCESS_DENIED_REASON, ACCESS_DENIED_MSG);
            }
        }
    }

    @Override
    public Map<String, RecordMetadata> get(List<String> ids) {
        Map<String, RecordMetadata> output = new HashMap<>();

        for (String id: ids) {
            RecordMetadataDoc doc = queryHelper.loadByPrimaryKey(RecordMetadataDoc.class, id);
            if (doc == null) continue;
            RecordMetadata rmd = doc.getMetadata();
            if (rmd == null) continue;
            if (userHasAccessToRecord(getGroups(), rmd.getAcl())) {
                output.put(id, rmd);
            } else {
                logger.error("User not in ACL for record %s");
            }
        }

        return output;
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
            String legalTagName, int limit, String cursor) {
        LegalTagAssociationDoc legalTagAssociationDoc = new LegalTagAssociationDoc();
        legalTagAssociationDoc.setLegalTag(legalTagName);
        QueryPageResult<LegalTagAssociationDoc> result = null;
        try {
            result = queryHelper.queryPage(LegalTagAssociationDoc.class,
                    legalTagAssociationDoc, 500, cursor);
        } catch (UnsupportedEncodingException e) {
            throw new AppException(org.apache.http.HttpStatus.SC_BAD_REQUEST, "Problem querying for legal tag", e.getMessage());
        }

        List<String> associatedRecordIds = new ArrayList<>();
        result.results.forEach(doc -> associatedRecordIds.add(doc.getRecordId())); // extract the Kinds from the SchemaDocs

        List<RecordMetadata> associatedRecords = new ArrayList<>();
        for(String recordId : associatedRecordIds){
            associatedRecords.add(get(recordId));
        }

        return new AbstractMap.SimpleEntry<>(result.cursor, associatedRecords);
    }

    /**
     * Unideal way to check if user has access to record because a list is being compared
     * for a match in a list. Future improvements include redesigning our dynamo schema to
     * get around this and redesigning dynamo schema to stop parsing the acl out of
     * recordmetadata
     * @param groups
     * @param acl
     * @return
     */
    // TODO: Optimize entitlements record ACL design to not compare list against list
    public boolean userHasAccessToRecord(Groups groups, Acl acl){
        HashSet<String> allowedGroups = new HashSet<>();
        for(String owner : acl.getOwners()){
            allowedGroups.add(owner);
        }
        for(String viewer : acl.getViewers()){
            allowedGroups.add(viewer);
        }
        List<GroupInfo> memberGroups = groups.getGroups();
        HashSet<String> memberGroupsSet = new HashSet<>();
        for(GroupInfo memberGroup : memberGroups){
            memberGroupsSet.add(memberGroup.getEmail());
        }

        return allowedGroups.stream().anyMatch(memberGroupsSet::contains);
    }

    // TODO: duplicate logic resides in EntitlementsAndCacheServiceImpl
    private Groups getGroups(){
        String cacheKey = this.cacheHelper.getGroupCacheKey(this.headers);
        Groups groups = this.cache.get(cacheKey);
        if(groups == null){
            groups = refreshGroups();
        }
        return groups;
    }

    private Groups refreshGroups(){
        Groups groups;
        IEntitlementsService service = this.factory.create(this.headers);
        try {
            groups = service.getGroups();
            this.cache.put(this.cacheHelper.getGroupCacheKey(this.headers), groups);
        } catch (EntitlementsException e) {
            e.printStackTrace();
            throw new AppException(e.getHttpResponse().getResponseCode(), ACCESS_DENIED_REASON, ACCESS_DENIED_MSG, e);
        }
        return groups;
    }

    private void saveLegalTagAssociation(String recordId, Set<String> legalTags){
        for(String legalTag : legalTags){
            LegalTagAssociationDoc doc = new LegalTagAssociationDoc();
            doc.setLegalTag(legalTag);
            doc.setRecordId(recordId);
            doc.setRecordIdLegalTag(String.format("%s:%s", recordId, legalTag));
            queryHelper.save(doc);
        }
    }

    private void deleteLegalTagAssociation(String recordId, String legalTag){
        LegalTagAssociationDoc doc = new LegalTagAssociationDoc();
        doc.setRecordIdLegalTag(String.format("%s:%s", recordId, legalTag));
        queryHelper.deleteByObject(doc);
    }
}
