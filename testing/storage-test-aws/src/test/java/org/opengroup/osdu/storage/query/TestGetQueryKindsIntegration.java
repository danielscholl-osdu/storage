// Copyright © 2020 Amazon Web Services
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

package org.opengroup.osdu.storage.query;

import org.junit.After;
import org.junit.Before;
import org.opengroup.osdu.storage.util.AWSTestUtils;
import org.opengroup.osdu.storage.util.SchemaUtil;
import org.opengroup.osdu.storage.util.TenantUtils;

import java.util.ArrayList;
import java.util.List;

public class TestGetQueryKindsIntegration extends GetQueryKindsIntegrationTests {

    private static List<String> schemaIds;

    // Need at least 3 to test limit of 2
    private static final int SCHEMA_COUNT = 3;

    protected static final String KIND_TEMPLATE = TenantUtils.getTenantName() + ":test:testkind:1.%d." + System.currentTimeMillis();

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AWSTestUtils();

        schemaIds = new ArrayList<>();
        for (int i = 0; i < SCHEMA_COUNT; i++) {
            String kind = String.format(KIND_TEMPLATE,i);
            SchemaUtil.create(kind, testUtils.getToken());
            schemaIds.add(kind);
        }
    }

    @After
    @Override
    public void tearDown() throws Exception {
        for (String kind : schemaIds) {
            SchemaUtil.delete(kind, testUtils.getToken());
        }

        this.testUtils = null;
	}
}