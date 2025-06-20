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

package org.opengroup.osdu.storage.provider.azure.repository.interfaces;

import com.azure.cosmos.models.SqlQuerySpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;

public interface PagingAndSortingRepository<T>  extends CrudRepository<T> {
    // Standard Spring Data Repository
    /*
    Iterable<T> findAll(Sort sort);
    Page<T> findAll(Pageable pageable);
    */
    Iterable<T> findAll(@NonNull Sort sort, String dataPartitionId, String cosmosDBName, String collection);
    Page<T> findAll(@NonNull Pageable pageable, String dataPartitionId, String cosmosDBName, String collection);
    Page<T> find(@NonNull Pageable pageable, String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query);
}
