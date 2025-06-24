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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;

/**
 * Test class for the main method of StorageAwsApplication.
 */
@ExtendWith(MockitoExtension.class)
class StorageAwsApplicationMainTest {

    @Test
    void mainMethodCallsSpringApplicationRun() {
        // Use Mockito to mock the static SpringApplication.run method
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            // Set up the mock to do nothing when run is called
            mocked.when(() -> SpringApplication.run(
                    Mockito.eq(StorageAwsApplication.class), 
                    Mockito.any(String[].class)))
                .thenReturn(null);

            // Call the main method
            String[] args = new String[0];
            StorageAwsApplication.main(args);

            // Verify that SpringApplication.run was called with the correct parameters
            mocked.verify(() -> SpringApplication.run(
                    Mockito.eq(StorageAwsApplication.class), 
                    Mockito.eq(args)), 
                    Mockito.times(1));
        }
    }
}
