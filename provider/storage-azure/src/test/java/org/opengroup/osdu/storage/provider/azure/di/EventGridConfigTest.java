// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.provider.azure.di;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class EventGridConfigTest {

    private static String VALID_TOPIC_NAME = "topicname";
    private static String INVALID_TOPIC_NAME = "";
    private static int VALID_BATCH_SIZE = 10;
    private static int INVALID_BATCH_SIZE = 0;

    @Test
    public void configurationValidationTests() {

        // Positive Case
        EventGridConfig eventGridConfig = new EventGridConfig(true, VALID_BATCH_SIZE, VALID_TOPIC_NAME);
        assertEquals(VALID_TOPIC_NAME, eventGridConfig.getTopicName());

        // Negative Cases
        RuntimeException runtimeException = Assertions.assertThrows(RuntimeException.class,
                () -> new EventGridConfig(true, VALID_BATCH_SIZE, INVALID_TOPIC_NAME));
        assertEquals("Missing EventGrid Configuration", runtimeException.getMessage());

        runtimeException =  Assertions.assertThrows(RuntimeException.class,
                () -> new EventGridConfig(true, INVALID_BATCH_SIZE, "topicName"));
        assertEquals("Missing EventGrid Configuration", runtimeException.getMessage());
    }
}
