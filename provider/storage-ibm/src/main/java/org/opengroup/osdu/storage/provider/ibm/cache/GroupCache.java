/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.storage.provider.ibm.cache;

import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.springframework.stereotype.Component;

@Component
public class GroupCache extends VmCache<String, Groups> {

    public GroupCache() {
        super(30, 1000);
    }
}

