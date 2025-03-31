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
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class ReplayAwsConfigTest {

    @InjectMocks
    private ReplayAwsConfig replayAwsConfig;

    @Test
    public void testAmazonSQSClient() {
        // Arrange
        ReflectionTestUtils.setField(replayAwsConfig, "region", "us-east-1");

        // Act
        AmazonSQS sqsClient = replayAwsConfig.amazonSQSClient();

        // Assert
        assertNotNull(sqsClient);
    }

    @Test
    public void testAmazonSNSClient() {
        // Arrange
        ReflectionTestUtils.setField(replayAwsConfig, "region", "us-east-1");

        // Act
        AmazonSNS snsClient = replayAwsConfig.amazonSNSClient();

        // Assert
        assertNotNull(snsClient);
    }

    @Test
    public void testAmazonDynamoDB() {
        // Arrange
        ReflectionTestUtils.setField(replayAwsConfig, "region", "us-east-1");

        // Act
        AmazonDynamoDB dynamoDB = replayAwsConfig.amazonDynamoDB();

        // Assert
        assertNotNull(dynamoDB);
    }

    @Test
    public void testDynamoDBMapper() {
        // Arrange
        ReflectionTestUtils.setField(replayAwsConfig, "region", "us-east-1");
        AmazonDynamoDB dynamoDB = replayAwsConfig.amazonDynamoDB();

        // Act
        DynamoDBMapper mapper = replayAwsConfig.dynamoDBMapper(dynamoDB);

        // Assert
        assertNotNull(mapper);
    }

    @Test
    public void testObjectMapper() {
        // Act
        ObjectMapper mapper = replayAwsConfig.objectMapper();

        // Assert
        assertNotNull(mapper);
    }
}
