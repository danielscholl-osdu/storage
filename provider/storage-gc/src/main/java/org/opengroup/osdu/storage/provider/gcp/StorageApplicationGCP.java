/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
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

import org.opengroup.osdu.storage.provider.gcp.messaging.config.MessagingCustomContextConfiguration;
import org.opengroup.osdu.storage.provider.gcp.web.config.WebAppMainContextConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.PropertySource;

@SpringBootConfiguration
@PropertySource("classpath:swagger.properties")
public class StorageApplicationGCP {

    /**
     * Storage application starts 2 application contexts at once, one for asynchronous message receiving via OQM that has its own context configuration that is
     * unbounded from request bean configurations and the second one for serving API that has request oriented bean configuration from common code.
     */
    public static void main(String[] args) {
        new SpringApplicationBuilder(StorageApplicationGCP.class)
            .sources(StorageApplicationGCP.class).web(WebApplicationType.NONE)
            .child(MessagingCustomContextConfiguration.class).web(WebApplicationType.NONE)
            .sibling(WebAppMainContextConfiguration.class).web(WebApplicationType.SERVLET)
            .run(args);
    }
}
