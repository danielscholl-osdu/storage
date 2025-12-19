/**
* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*      http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.opengroup.osdu.storage.records;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroup.osdu.storage.util.AWSTestUtils;
import org.opengroup.osdu.storage.util.ConfigUtils;

public class TestDataRootAccess extends DataRootAccessTest {

    @BeforeClass
    public static void classSetup() throws Exception {
        AWSTestUtils testUtils = new AWSTestUtils();
        DataRootAccessTest.classSetup(testUtils.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        AWSTestUtils testUtils = new AWSTestUtils();
        DataRootAccessTest.classTearDown(testUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AWSTestUtils();
        this.configUtils = new ConfigUtils("test.properties");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
        this.configUtils = null;
    }
}
