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

package org.opengroup.osdu.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test class verifies the annotations on the StorageAwsApplication class
 * without loading the Spring context for faster execution.
 */
class StorageAwsApplicationNoContextTest {

    @Test
    void verifySpringBootApplicationAnnotation() {
        boolean hasAnnotation = StorageAwsApplication.class.isAnnotationPresent(SpringBootApplication.class);
        assertTrue(hasAnnotation, "StorageAwsApplication should have @SpringBootApplication annotation");
    }

    @Test
    void verifyPropertySourceAnnotation() {
        PropertySource annotation = StorageAwsApplication.class.getAnnotation(PropertySource.class);
        assertNotNull(annotation, "StorageAwsApplication should have @PropertySource annotation");
        assertEquals("classpath:swagger.properties", annotation.value()[0], "PropertySource should point to classpath:swagger.properties");
    }

    @Test
    void verifyComponentScanAnnotation() {
        ComponentScan annotation = StorageAwsApplication.class.getAnnotation(ComponentScan.class);
        assertNotNull(annotation, "StorageAwsApplication should have @ComponentScan annotation");
        assertEquals("org.opengroup.osdu", annotation.value()[0], "ComponentScan should include org.opengroup.osdu package");
    }

    @Test
    void verifyEnableSchedulingAnnotation() {
        boolean hasAnnotation = StorageAwsApplication.class.isAnnotationPresent(EnableScheduling.class);
        assertTrue(hasAnnotation, "StorageAwsApplication should have @EnableScheduling annotation");
    }
}
