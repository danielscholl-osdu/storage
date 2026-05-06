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

import org.opengroup.osdu.azure.di.RedisAzureConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RedisConfig {

    private final int database;
    private final int commandTimeout;
    private final int port;
    private final int expiration;
    private final String hostKey;
    private final String passwordKey;
    private final String principalId;
    private final String hostname;
    private final long schemaTtl;
    private final long groupTtl;
    private final long cursorTtl;

    public RedisConfig(
            @Value("${redis.database}") int database,
            @Value("${redis.command.timeout:5}") int commandTimeout,
            @Value("${redis.port:6380}") int port,
            @Value("${redis.expiration:3600}") int expiration,
            @Value("${redis.host.key}") String hostKey,
            @Value("${redis.password.key}") String passwordKey,
            @Value("${redis.principal.id:#{null}}") String principalId,
            @Value("${redis.hostname:#{null}}") String hostname,
            @Value("${redis.schema.ttl:3600}") long schemaTtl,
            @Value("${redis.group.ttl:30}") long groupTtl,
            @Value("${redis.cursor.ttl:90}") long cursorTtl) {
        this.database = database;
        this.commandTimeout = commandTimeout;
        this.port = port;
        this.expiration = expiration;
        this.hostKey = hostKey;
        this.passwordKey = passwordKey;
        this.principalId = principalId;
        this.hostname = hostname;
        this.schemaTtl = schemaTtl;
        this.groupTtl = groupTtl;
        this.cursorTtl = cursorTtl;
    }

    public RedisAzureConfiguration createSchemaConfiguration() { return createConfiguration(schemaTtl); }

    public RedisAzureConfiguration createGroupConfiguration() { return createConfiguration(groupTtl); }

    public RedisAzureConfiguration createCursorConfiguration() { return createConfiguration(cursorTtl); }

    private RedisAzureConfiguration createConfiguration(long timeout) {
        return new RedisAzureConfiguration(
            database,
            expiration,
            port,
            timeout,
            commandTimeout,
            hostKey,
            passwordKey,
            principalId,
            hostname);
    }
}
