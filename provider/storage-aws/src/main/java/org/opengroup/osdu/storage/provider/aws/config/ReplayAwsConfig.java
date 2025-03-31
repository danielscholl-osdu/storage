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
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class for AWS replay feature.
 * This class provides the necessary beans for the replay functionality.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayAwsConfig {
    
    @Value("${aws.region}")
    private String region;
    
    /**
     * Creates an AmazonSQS client bean if not already defined.
     *
     * @return The AmazonSQS client
     */
    @Bean
    @ConditionalOnMissingBean
    public AmazonSQS amazonSQSClient() {
        return AmazonSQSClientBuilder.standard()
            .withRegion(region)
            .build();
    }
    
    /**
     * Creates an AmazonSNS client bean if not already defined.
     *
     * @return The AmazonSNS client
     */
    @Bean
    @ConditionalOnMissingBean
    public AmazonSNS amazonSNSClient() {
        return AmazonSNSClientBuilder.standard()
            .withRegion(region)
            .build();
    }
    
    /**
     * Creates a DynamoDBMapper bean if not already defined.
     *
     * @param amazonDynamoDB The AmazonDynamoDB client
     * @return The DynamoDBMapper
     */
    @Bean
    @ConditionalOnMissingBean
    public DynamoDBMapper dynamoDBMapper(AmazonDynamoDB amazonDynamoDB) {
        return new DynamoDBMapper(amazonDynamoDB);
    }
    
    /**
     * Creates an AmazonDynamoDB client bean if not already defined.
     *
     * @return The AmazonDynamoDB client
     */
    @Bean
    @ConditionalOnMissingBean
    public AmazonDynamoDB amazonDynamoDB() {
        return AmazonDynamoDBClientBuilder.standard()
            .withRegion(region)
            .build();
    }
    
    /**
     * Creates an ObjectMapper bean if not already defined.
     *
     * @return The ObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
