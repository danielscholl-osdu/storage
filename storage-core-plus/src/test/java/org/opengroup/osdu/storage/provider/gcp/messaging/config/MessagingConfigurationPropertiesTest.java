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

class MessagingConfigurationPropertiesTest {

    @Test
    void equals_BasicContract() {
        MessagingConfigurationProperties config = new MessagingConfigurationProperties();

        assertEquals(config, config);
        assertNotEquals(null, config);
        assertNotEquals(config, new Object());
    }

    @Test
    void testMultipleSettersOnSameInstance() {
        MessagingConfigurationProperties config = new MessagingConfigurationProperties();

        config.setLegalTagsChangedTopicName("topic1");
        assertEquals("topic1", config.getLegalTagsChangedTopicName());

        config.setLegalTagsChangedTopicName("topic2");
        assertEquals("topic2", config.getLegalTagsChangedTopicName());

        config.setLegalTagsChangedTopicName(null);
        assertNull(config.getLegalTagsChangedTopicName());
    }

    @Test
    void testEqualsSymmetry() {
        MessagingConfigurationProperties c1 = create("topic", "sub", "email@example.com");
        MessagingConfigurationProperties c2 = create("topic", "sub", "email@example.com");
        assertEquals(c1, c2);
        assertEquals(c2, c1);
    }

    @Test
    void testHashCodeConsistency() {
        MessagingConfigurationProperties config = create("topic", "sub", "email@example.com");
        int hash1 = config.hashCode();
        int hash2 = config.hashCode();
        assertEquals(hash1, hash2);
    }

    @Test
    void testSpecialCharactersInFields() {
        MessagingConfigurationProperties config = new MessagingConfigurationProperties();
        config.setLegalTagsChangedTopicName("topic-with-special-chars!@#$%");
        config.setLegalTagsChangedSubscriptionName("subscription_with_underscores_123");
        config.setStorageServiceAccountEmail("test+alias@sub-domain.example.com");

        assertEquals("topic-with-special-chars!@#$%", config.getLegalTagsChangedTopicName());
        assertEquals("subscription_with_underscores_123", config.getLegalTagsChangedSubscriptionName());
        assertEquals("test+alias@sub-domain.example.com", config.getStorageServiceAccountEmail());
    }

    private MessagingConfigurationProperties create(String topicName, String subscriptionName, String email) {
        MessagingConfigurationProperties config = new MessagingConfigurationProperties();
        config.setLegalTagsChangedTopicName(topicName);
        config.setLegalTagsChangedSubscriptionName(subscriptionName);
        config.setStorageServiceAccountEmail(email);
        return config;
    }
}
