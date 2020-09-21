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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AWSTestUtils;
import org.opengroup.osdu.storage.util.SchemaUtil;
import org.opengroup.osdu.storage.util.TenantUtils;

import java.util.ArrayList;
import java.util.List;

public class TestStorageQuerySuccessful extends StorageQuerySuccessfulTest {

    private static final AWSTestUtils awsTestUtils = new AWSTestUtils();

    private static List<String> schemaIds;

    // Need at least 11 to require a cursor when querying for 10 items
    private static final int SCHEMA_COUNT = 11;

    protected static final String KIND_TEMPLATE = TenantUtils.getTenantName() + ":test:testkind:1.%d." + System.currentTimeMillis();

    @BeforeClass
	public static void classSetup() throws Exception {
        StorageQuerySuccessfulTest.classSetup(awsTestUtils.getToken());

        schemaIds = new ArrayList<>();
        for (int i = 0; i < SCHEMA_COUNT; i++) {
            String kind = String.format(KIND_TEMPLATE,i);
            SchemaUtil.create(kind, awsTestUtils.getToken());
            schemaIds.add(kind);
        }
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        for (String kind : schemaIds) {
            SchemaUtil.delete(kind, awsTestUtils.getToken());
        }

        StorageQuerySuccessfulTest.classTearDown(awsTestUtils.getToken());
    }
	
    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AWSTestUtils();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
	}
}