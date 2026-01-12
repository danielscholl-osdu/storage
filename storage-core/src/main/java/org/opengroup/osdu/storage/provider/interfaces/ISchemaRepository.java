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

package org.opengroup.osdu.storage.provider.interfaces;

import org.opengroup.osdu.core.common.model.storage.Schema;

public interface ISchemaRepository {
	String SCHEMA_KIND = "StorageSchema";

	String SCHEMA = "schema";
	String USER = "user";
	String EXTENSION = "extension";

	void add(Schema schema, String user);

	Schema get(String kind);

	void delete(String kind);
}
