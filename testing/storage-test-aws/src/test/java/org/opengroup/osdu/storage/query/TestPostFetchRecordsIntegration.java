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

public class TestPostFetchRecordsIntegration extends PostFetchRecordsIntegrationTests {

    private static final AWSTestUtils awsTestUtils = new AWSTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        PostFetchRecordsIntegrationTests.classSetup(awsTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        PostFetchRecordsIntegrationTests.classTearDown(awsTestUtils.getToken());
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

	@Override
    public void should_returnConvertedRecords_whenConversionRequiredAndNoError() {
        // ignoring this test until there is a CRS conversion service in place
        // for AWS to use
    }

    @Override
    public void should_returnConvertedRecords_whenConversionRequiredAndNoErrorWithMultiplePairOfCoordinates() {
        // ignoring this test until there is a CRS conversion service in place
        // for AWS to use
    }

    @Override
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndNestedPropertyProvidedInMetaBlock() {
        // ignoring this test until there is a CRS conversion service in place
        // for AWS to use
    }

    @Override
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndNestedPropertyProvidedInMetaBlock1() {
        // ignoring this test until there is a CRS conversion service in place
        // for AWS to use
    }
}
