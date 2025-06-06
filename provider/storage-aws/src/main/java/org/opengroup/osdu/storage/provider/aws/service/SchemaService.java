/*
 * Copyright © Amazon Web Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.service;

import org.opengroup.osdu.storage.provider.aws.model.schema.SchemaInfoResponse;

import java.util.List;

/**
 * Interface to consume schemas from the Schema Service
 */
public interface SchemaService {
    /**
     * Get all schemas from the Schema Service
     * @return SchemaInfoResponse containing all schemas
     */
    SchemaInfoResponse getAllSchemas();
    
    /**
     * Get all unique entity types (kinds) from the Schema Service
     * @return List of unique entity types
     */
    List<String> getAllKinds();
}
