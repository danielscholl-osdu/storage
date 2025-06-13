package org.opengroup.osdu.storage.provider.azure.util;

import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.azure.EntitlementsAndCacheServiceAzure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.EMPTY;

@Component
public class EntitlementsHelper {

    private final Logger logger = LoggerFactory.getLogger(EntitlementsHelper.class);

    @Autowired
    private EntitlementsAndCacheServiceAzure dataEntitlementsService;
    @Autowired
    private DpsHeaders headers;

    public boolean hasOwnerAccessToRecord(RecordMetadata record) {
        String[] acls = ofNullable(record)
                .map(RecordMetadata::getAcl)
                .map(Acl::getOwners).
                orElseGet(() -> {
                    logger.error("Record {} doesn't contain acl owners or acl block has wrong structure", record == null ? EMPTY : record.getId());
                    return new String[]{};
                });
        return dataEntitlementsService.hasAccessToData(headers,
                new HashSet<>(Arrays.asList(acls)));
    }

    public boolean hasViewerAccessToRecord(RecordMetadata record) {
        String[] acls = ofNullable(record)
                .map(RecordMetadata::getAcl)
                .map(Acl::getViewers).
                orElseGet(() -> {
                    logger.error("Record {} doesn't contain acl viewers or acl block has wrong structure", record == null ? EMPTY : record.getId());
                    return new String[]{};
                });
        boolean isEntitledForViewing = dataEntitlementsService.hasAccessToData(headers,
                new HashSet<>(Arrays.asList(acls)));
        boolean isRecordCreator = ofNullable(record)
                .map(RecordMetadata::getUser)
                .map(user -> user.equalsIgnoreCase(headers.getUserEmail()))
                .orElse(false);
        if (!isEntitledForViewing && !isRecordCreator) {
            return hasOwnerAccessToRecord(record);
        }
        return true;
    }

}
