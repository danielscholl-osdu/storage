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

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;


import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

// <K> is a serializable (e.g a Cursor, com.google.cloud.datastore.Cursor in case of gcp implementation)
public interface IRecordsMetadataRepository<K extends Serializable> {
	List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata);

	void delete(String id);

	RecordMetadata get(String id);

	Map<String, RecordMetadata> get(List<String> ids);

	//TODO remove after all providers replace it with the new method queryByLegal
	AbstractMap.SimpleEntry<K, List<RecordMetadata>> queryByLegalTagName(String legalTagName, int limit, K cursor);

	AbstractMap.SimpleEntry<K, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit);
}