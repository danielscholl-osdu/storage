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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.provider.gcp.web.config.GcpAppServiceConfig;

class GcpAppServiceConfigTest {

    @Test
    void equals_BasicContract() {
        GcpAppServiceConfig c = new GcpAppServiceConfig();

        assertEquals(c, c);
        assertNotEquals(null, c);
        assertNotEquals(c, new Object());
    }

    @Test
    void testRedisStorageDefaultValues() {
        GcpAppServiceConfig config = new GcpAppServiceConfig();
        assertEquals(3600, config.getRedisStorageExpiration());
        assertEquals(Boolean.FALSE, config.getRedisStorageWithSsl());
    }

    @Test
    void testRedisGroupDefaultValues() {
        GcpAppServiceConfig config = new GcpAppServiceConfig();
        assertEquals(30, config.getRedisGroupExpiration());
        assertEquals(Boolean.FALSE, config.getRedisGroupWithSsl());
    }

    @Test
    void testMultipleConfigurationsIndependent() {
        GcpAppServiceConfig config1 = new GcpAppServiceConfig();
        config1.setPubsubSearchTopic("topic1");
        config1.setRedisStorageHost("host1");

        GcpAppServiceConfig config2 = new GcpAppServiceConfig();
        config2.setPubsubSearchTopic("topic2");
        config2.setRedisStorageHost("host2");

        assertNotSame(config1, config2);
        assertEquals("topic1", config1.getPubsubSearchTopic());
        assertEquals("topic2", config2.getPubsubSearchTopic());
    }
}
