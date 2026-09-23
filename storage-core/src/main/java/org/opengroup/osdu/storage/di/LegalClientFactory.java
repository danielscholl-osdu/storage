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

package org.opengroup.osdu.storage.di;

import org.opengroup.osdu.core.common.http.json.HttpResponseBodyMapper;
import org.opengroup.osdu.core.common.legal.ILegalFactory;
import org.opengroup.osdu.core.common.legal.LegalAPIConfig;
import org.opengroup.osdu.core.common.legal.LegalFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.stereotype.Component;

@Component
public class LegalClientFactory extends AbstractFactoryBean<ILegalFactory> {

	@Value("${LEGALTAG_API}")
	private String LEGALTAG_API;

	@Override
	public Class<?> getObjectType() {
		return ILegalFactory.class;
	}

	@Autowired
	private HttpResponseBodyMapper httpResponseBodyMapper;

	@Override
	protected ILegalFactory createInstance() throws Exception {
		return new LegalFactory(LegalAPIConfig
				.builder()
				.rootUrl(LEGALTAG_API)
				.build(), httpResponseBodyMapper);
	}
}