/*
 * Copyright © Amazon Web Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RestTemplateConfigTest {

    @Test
    public void testRestTemplateCreation() {
        // Arrange
        RestTemplateConfig config = new RestTemplateConfig();
        
        // Act
        RestTemplate restTemplate = config.restTemplate();
        
        // Assert
        assertNotNull(restTemplate, "RestTemplate should not be null");
    }
    
    @Test
    public void testRestTemplateIsProperlyConfigured() {
        // Arrange
        RestTemplateConfig config = new RestTemplateConfig();
        
        // Act
        RestTemplate restTemplate = config.restTemplate();
        
        // Assert
        // The default RestTemplate should have default message converters
        assertNotNull(restTemplate.getMessageConverters(), "Message converters should not be null");
        // Verify that the default message converters are present
        // Default RestTemplate typically has 7 or more converters
        assert(restTemplate.getMessageConverters().size() > 0);
    }
}
