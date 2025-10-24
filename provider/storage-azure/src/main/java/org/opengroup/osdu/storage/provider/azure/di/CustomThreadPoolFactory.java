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

import org.opengroup.osdu.azure.concurrency.CustomExecutors;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Primary
@Component
public class CustomThreadPoolFactory extends AbstractFactoryBean<ExecutorService> {

    @Override
    public Class<?> getObjectType() {
        return ExecutorService.class;
    }

    @Override
    protected ExecutorService  createInstance() throws Exception {
        return CustomExecutors.newFixedThreadPool(192);
    }
}
