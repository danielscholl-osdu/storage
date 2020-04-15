// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.storage.provider.gcp.di;

import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.gcp.multitenancy.credentials.DatastoreCredential;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.stereotype.Component;

@Component
public class DatastoreCredentialsCacheFactory extends AbstractFactoryBean<ICache<String, DatastoreCredential>> {

	@Override
	public Class<?> getObjectType() {
		return ICache.class;
	}

	@Override
	protected ICache<String, DatastoreCredential> createInstance() throws Exception {
		return new VmCache<>(5 * 60, 20);
	}
}

