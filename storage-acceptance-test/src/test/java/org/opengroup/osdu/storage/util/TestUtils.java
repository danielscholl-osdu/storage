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

package org.opengroup.osdu.storage.util;

import lombok.Getter;
import org.opengroup.osdu.core.test.config.EnvLoader;
import org.opengroup.osdu.core.test.client.model.storage.CopyRecordsRequest;

public class TestUtils {

    public static final String STORAGE_TEST_GROUP_ENT_V_2 = "data.storage-integration-test-acl.ent-v2";
    public static final String STORAGE_TEST_GROUP_ENT_V_2_DESCRIPTION = "EntV2 Storage test group.";

    @Getter
    private static final String groupId = EnvLoader.get("ENTITLEMENTS_DOMAIN");
    private static final String tenantId = EnvLoader.get("DATA_PARTITION_ID");

    public static String getAclSuffix() {
        return String.format("%s.%s",tenantId, groupId);
    }

    /**
     * @deprecated Use dynamically created groups from test classes instead of hardcoded values.
     * This method returns a hardcoded group email and should not be used in new tests.
     */
    @Deprecated
    public static  String getAcl() {
        return String.format("data.test1@%s", getAclSuffix());
    }

    public static  String getEntV2OnlyAcl() {
        return String.format(STORAGE_TEST_GROUP_ENT_V_2 + "@%s", getAclSuffix());
    }

    public static CopyRecordsRequest getCopyRecordRequest(String target, String recordId) {
        return CopyRecordsRequest.of(target, recordId);
    }
}
