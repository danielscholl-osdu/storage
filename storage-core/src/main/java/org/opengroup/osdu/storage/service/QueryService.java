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

package org.opengroup.osdu.storage.service;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.RecordVersions;

import java.util.Optional;

public interface QueryService {

	String getRecordInfo(String id, String[] attributes, Optional<CollaborationContext> collaborationContext);

	String getRecordInfo(String id, long version, String[] attributes, Optional<CollaborationContext> collaborationContext);

	RecordVersions listVersions(String id, Optional<CollaborationContext> collaborationContext);
}
