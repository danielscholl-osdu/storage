/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.util;

import com.amazonaws.ClientConfiguration;
import org.opengroup.osdu.core.aws.configurationsetup.ConfigSetup;
import org.opengroup.osdu.core.common.logging.DefaultLogger;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class WorkerThreadPool {

    private static final JaxRsDpsLog logger = new JaxRsDpsLog(new DefaultLogger(), new DpsHeaders());
    private static final int DEFAULT_THREADS = 1000;
    @Autowired
    public WorkerThreadPool(@Value("${aws.worker-threads}") int numberOfThreads) {
        if (numberOfThreads <= 0) {
            logger.error(String.format("Illegal `aws.worker-threads` value: %d. Using default %d threads instead.", numberOfThreads, DEFAULT_THREADS));
            numberOfThreads = DEFAULT_THREADS;
        }
        threadPool = Executors.newFixedThreadPool(numberOfThreads);
        logger.info(String.format("Created the Worker Thread Pool with %d threads", numberOfThreads));
        clientConfiguration = ConfigSetup.setUpConfig().withMaxConnections(numberOfThreads);
    }

    private final ClientConfiguration clientConfiguration;

    public ClientConfiguration getClientConfiguration() {
        return clientConfiguration;
    }

    private final ExecutorService threadPool;

    public ExecutorService getThreadPool() {
        return threadPool;
    }
}
