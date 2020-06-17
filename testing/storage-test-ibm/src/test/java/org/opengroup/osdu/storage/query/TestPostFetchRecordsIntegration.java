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
import org.junit.Ignore;
import org.junit.Test;
import org.opengroup.osdu.storage.util.IBMTestUtils;

public class TestPostFetchRecordsIntegration extends PostFetchRecordsIntegrationTests {

    private static final IBMTestUtils ibmTestUtils = new IBMTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        PostFetchRecordsIntegrationTests.classSetup(ibmTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        PostFetchRecordsIntegrationTests.classTearDown(ibmTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new IBMTestUtils();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
	}    
    
    @Ignore
    @Test
    public void should_returnConvertedRecords_whenConversionRequiredAndNoError() { }
    
    @Ignore
    @Test
    public void should_returnOriginalRecordsAndConversionStatusAsNoMeta_whenConversionRequiredAndNoMetaBlockInRecord() { }    
    
    @Ignore
    @Test
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndNestedPropertyProvidedInMetaBlock() { }

    @Ignore
    @Test
    public void should_returnRecordsAndConversionStatus_whenConversionRequiredAndConversionErrorExists() { }
    
    @Ignore
    @Test
    public void should_returnConvertedRecords_whenConversionRequiredAndNoErrorWithMultiplePairOfCoordinates() throws Exception {}

}
