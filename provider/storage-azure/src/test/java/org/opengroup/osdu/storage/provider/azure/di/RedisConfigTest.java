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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.azure.di.RedisAzureConfiguration;

class RedisConfigTest {

    @Test
    void shouldApplyCacheTtlsAndConnectionTimeoutToCorrectFields() {
        RedisConfig config = new RedisConfig(
            0,
            5,
            6380,
            7,
            "host-key",
            "password-key",
            "principal-id",
            "hostname",
            101,
            202,
            303);

        RedisAzureConfiguration schema = config.createSchemaConfiguration();
        RedisAzureConfiguration group = config.createGroupConfiguration();
        RedisAzureConfiguration cursor = config.createCursorConfiguration();

        assertAll(
            () -> assertEquals(101, schema.getExpiration()),
            () -> assertEquals(202, group.getExpiration()),
            () -> assertEquals(303, cursor.getExpiration()),
            () -> assertEquals(7, schema.getConnectionTimeout()),
            () -> assertEquals(7, group.getConnectionTimeout()),
            () -> assertEquals(7, cursor.getConnectionTimeout()));
    }
}