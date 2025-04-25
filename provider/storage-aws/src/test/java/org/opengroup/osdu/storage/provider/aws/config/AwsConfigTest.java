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

package org.opengroup.osdu.storage.provider.aws.config;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.dynamodb.IDynamoDBConfig;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AwsConfigTest {

    @InjectMocks
    private AwsConfig awsConfig;

    @Mock
    private IDynamoDBConfig iDynamoDBConfig;

    @Mock
    private DynamoDBMapper dynamoDBMapper;

    @Mock
    private AmazonDynamoDB amazonDynamoDB;

    private static final String REGION = "us-east-1";
    private static final String DYNAMODB_ENDPOINT = "";
    private static final String TABLE_NAME = "services/core/storage/RecordMetadataTable";

    @Before
    public void setUp() {
        // Set up fields using reflection
        ReflectionTestUtils.setField(awsConfig, "region", REGION);
        ReflectionTestUtils.setField(awsConfig, "dynamoDbEndpoint", DYNAMODB_ENDPOINT);
        ReflectionTestUtils.setField(awsConfig, "tableName", TABLE_NAME);
        
        // Mock behavior
        when(iDynamoDBConfig.DynamoDBMapper()).thenReturn(dynamoDBMapper);
        when(iDynamoDBConfig.amazonDynamoDB()).thenReturn(amazonDynamoDB);
    }

    @Test
    public void testDynamoDBMapper() {
        // Execute
        DynamoDBMapper result = awsConfig.dynamoDBMapper(iDynamoDBConfig);
        
        // Verify
        assertNotNull(result);
        assertEquals(dynamoDBMapper, result);
        verify(iDynamoDBConfig).DynamoDBMapper();
    }

    @Test
    public void testAmazonDynamoDB() {
        // Execute
        AmazonDynamoDB result = awsConfig.amazonDynamoDB(iDynamoDBConfig);
        
        // Verify
        assertNotNull(result);
        assertEquals(amazonDynamoDB, result);
        verify(iDynamoDBConfig).amazonDynamoDB();
    }
}
