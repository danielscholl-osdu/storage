/*
 *  Copyright @ Microsoft Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.opengroup.osdu.storage.provider.gcp.messaging.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.provider.gcp.web.config.PartitionPropertyNames;
import org.springframework.boot.context.properties.ConfigurationProperties;

@DisplayName("PartitionPropertyNames Tests")
class PartitionPropertyNamesTest {

    @Test
    @DisplayName("Should have correct prefix in @ConfigurationProperties")
    void shouldHaveCorrectPrefix() {
        // Act
        ConfigurationProperties annotation = PartitionPropertyNames.class
                .getAnnotation(ConfigurationProperties.class);

        // Assert
        assertNotNull(annotation, "ConfigurationProperties annotation should not be null");
        assertEquals("partition.properties", annotation.prefix(),
                "ConfigurationProperties prefix should be 'partition.properties'");
    }

    @Test
    @DisplayName("Should handle various bucket name formats")
    void shouldHandleVariousBucketNameFormats() {
        // Arrange
        PartitionPropertyNames partitionPropertyNames = new PartitionPropertyNames();
        String[] bucketNames = {
                "simple-bucket",
                "bucket_with_underscore",
                "bucket.with.dots",
                "bucket-123-with-numbers",
                "UPPERCASE-BUCKET",
                "MixedCase-Bucket"
        };

        // Act & Assert
        for (String bucketName : bucketNames) {
            partitionPropertyNames.setStorageBucketName(bucketName);
            assertEquals(bucketName, partitionPropertyNames.getStorageBucketName(),
                    "Should handle bucket name: " + bucketName);
        }
    }

    @Test
    @DisplayName("Should create multiple independent instances")
    void shouldCreateMultipleIndependentInstances() {
        // Act
        PartitionPropertyNames first = new PartitionPropertyNames();
        first.setStorageBucketName("first-bucket");

        PartitionPropertyNames second = new PartitionPropertyNames();
        second.setStorageBucketName("second-bucket");

        // Assert
        assertNotSame(first, second, "Instances should be different objects");
        assertEquals("first-bucket", first.getStorageBucketName());
        assertEquals("second-bucket", second.getStorageBucketName());
    }
}
