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

package org.opengroup.osdu.storage.provider.aws.model.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;


// Move to core common
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SchemaIdentity {
    private String authority;
    private String source;
    private String entityType;
    private int schemaVersionMajor;
    private int schemaVersionMinor;
    private int schemaVersionPatch;
    private String id;
}
