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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ReplayConfigurationPropertiesTest {

    @Test
    void equals_BasicContract() {
        ReplayConfigurationProperties config = new ReplayConfigurationProperties();

        assertEquals(config, config);
        assertNotEquals(null, config);
        assertNotEquals(config, new Object());
    }

    @Test
    void testMultipleSettersOnSameInstance() {
        ReplayConfigurationProperties config = new ReplayConfigurationProperties();

        config.setDeadLetterTopicName("topic1");
        assertEquals("topic1", config.getDeadLetterTopicName());

        config.setDeadLetterTopicName("topic2");
        assertEquals("topic2", config.getDeadLetterTopicName());

        config.setDeadLetterTopicName(null);
        assertNull(config.getDeadLetterTopicName());
    }

    @Test
    void testEqualsSymmetry() {
        ReplayConfigurationProperties c1 = create("topic", "sub");
        ReplayConfigurationProperties c2 = create("topic", "sub");
        assertEquals(c1, c2);
        assertEquals(c2, c1);
    }

    @Test
    void testHashCodeConsistency() {
        ReplayConfigurationProperties config = create("topic", "sub");
        int hash1 = config.hashCode();
        int hash2 = config.hashCode();
        assertEquals(hash1, hash2);
    }

    private ReplayConfigurationProperties create(String topicName, String subscriptionName) {
        ReplayConfigurationProperties config = new ReplayConfigurationProperties();
        config.setDeadLetterTopicName(topicName);
        config.setDeadLetterSubscriptionName(subscriptionName);
        return config;
    }
}
