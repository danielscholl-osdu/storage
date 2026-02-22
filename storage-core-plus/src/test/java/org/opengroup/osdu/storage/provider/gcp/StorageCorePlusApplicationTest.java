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
package org.opengroup.osdu.storage.provider.gcp;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.PropertySource;

@ExtendWith(MockitoExtension.class)
@DisplayName("StorageCorePlusApplication Tests")
class StorageCorePlusApplicationTest {

    @Test
    @DisplayName("Should have correct PropertySource value")
    void shouldHaveCorrectPropertySourceValue() {

        PropertySource propertySource = StorageCorePlusApplication.class
                .getAnnotation(PropertySource.class);

        assertNotNull(propertySource, "PropertySource annotation should not be null");
        String[] values = propertySource.value();
        assertEquals(1, values.length, "Should have exactly one property source");
        assertEquals("classpath:swagger.properties", values[0],
                "PropertySource should be 'classpath:swagger.properties'");
    }

    @Test
    @DisplayName("Should configure sources with StorageCorePlusApplication class")
    void shouldConfigureSourcesWithStorageCorePlusApplication() {
        String[] args = {};
        ArgumentCaptor<Class<?>> classCaptor = ArgumentCaptor.forClass(Class.class);

        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.sibling(any(Class.class))).thenReturn(mock);
                    when(mock.run(any(String[].class))).thenReturn(mock(ConfigurableApplicationContext.class));
                })) {

            StorageCorePlusApplication.main(args);

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);
            verify(builder).sources(classCaptor.capture());
            assertEquals(StorageCorePlusApplication.class, classCaptor.getValue(),
                    "Should configure sources with StorageCorePlusApplication.class");
        }
    }
}
