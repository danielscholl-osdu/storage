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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutorService;

class WorkerThreadPoolTest {

    @Test
    void should_reuse_executor_if_exists() {
        WorkerThreadPool workerPool = new WorkerThreadPool(7);
        ExecutorService service = workerPool.getThreadPool();

        assertNotNull(service);
        assertEquals(service, workerPool.getThreadPool());
    }

    @Test
    void should_not_throw_exception_if_numberOfThreads_is_invalid() {
        WorkerThreadPool workerPool = new WorkerThreadPool(-1);
        assertNotNull(workerPool.getThreadPool());
    }
}
