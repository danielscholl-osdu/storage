// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

public class TestGetQueryInfoIntegration extends GetQueryInfoIntegrationTest {

  private static final AWSTestUtils awsTestUtils = new AWSTestUtils();

  @BeforeClass
  public static void classSetup() throws Exception {
    GetQueryRecordsIntegrationTest.classSetup(awsTestUtils.getToken());
  }

  @AfterClass
  public static void classTearDown() throws Exception {
    GetQueryRecordsIntegrationTest.classTearDown(awsTestUtils.getToken());
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
