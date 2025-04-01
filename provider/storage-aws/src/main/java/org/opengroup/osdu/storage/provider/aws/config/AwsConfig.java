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
import org.opengroup.osdu.core.aws.configurationsetup.ConfigSetup;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBConfigV2;
import org.opengroup.osdu.core.aws.dynamodb.IDynamoDBConfig;
import org.opengroup.osdu.core.aws.sns.AmazonSNSConfig;
import org.opengroup.osdu.core.aws.sqs.AmazonSQSConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Main AWS configuration class that provides beans for AWS services.
 */
@Configuration
public class AwsConfig {
    
    @Value("${AWS.REGION}")
    private String region;
    
    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;
    
    @Value("${aws.dynamodb.recordMetadataTable.ssm.relativePath}")
    private String tableName;
    
    /**
     * Creates an AmazonSQS client bean.
     *
     * @return The AmazonSQS client
     */
    @Bean
    @Primary
    public AmazonSQS amazonSQSClient() {
        AmazonSQSConfig sqsConfig = new AmazonSQSConfig(region);
        return sqsConfig.AmazonSQS();
    }
    
    /**
     * Creates an AmazonSNS client bean.
     *
     * @return The AmazonSNS client
     */
    @Bean
    @Primary
    public AmazonSNS amazonSNSClient() {
        AmazonSNSConfig snsConfig = new AmazonSNSConfig(region);
        return snsConfig.AmazonSNS();
    }
    
    /**
     * Creates a DynamoDB configuration bean.
     *
     * @return The IDynamoDBConfig implementation
     */
    @Bean
    @Primary
    public IDynamoDBConfig dynamoDBConfig() {
        return new DynamoDBConfigV2(
            dynamoDbEndpoint.isEmpty() ? "https://dynamodb." + region + ".amazonaws.com" : dynamoDbEndpoint,
            region,
            tableName,
            ConfigSetup.setUpConfig()
        );
    }
    
    /**
     * Creates a DynamoDBMapper bean.
     *
     * @param dynamoDBConfig The DynamoDB configuration
     * @return The DynamoDBMapper
     */
    @Bean
    @Primary
    public DynamoDBMapper dynamoDBMapper(IDynamoDBConfig dynamoDBConfig) {
        return dynamoDBConfig.DynamoDBMapper();
    }
    
    /**
     * Creates an AmazonDynamoDB client bean.
     *
     * @param dynamoDBConfig The DynamoDB configuration
     * @return The AmazonDynamoDB client
     */
    @Bean
    @Primary
    public AmazonDynamoDB amazonDynamoDB(IDynamoDBConfig dynamoDBConfig) {
        return dynamoDBConfig.amazonDynamoDB();
    }
}
