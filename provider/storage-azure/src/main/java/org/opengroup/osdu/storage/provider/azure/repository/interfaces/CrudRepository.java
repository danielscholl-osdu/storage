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
import org.springframework.lang.NonNull;

import java.util.List;

public interface CrudRepository<T> extends Repository<T> {
        // Standard Spring Data Repository
        /*
        <T> T save(T entity);

        <T> Iterable<T> saveAll(Iterable<T> entities);

        //Optional<T> findById(String id);

        boolean existsById(String id);

        Iterable<T> findAll();

        Iterable<T> findAllById(Iterable<String> ids);

        long count();

        //void deleteById(String id);

        //void delete(T entity);

        //void deleteAll(Iterable<T> entities);

        //void deleteAll();
        */

    T save(T entity, String dataPartitionId, String cosmosDBName, String collection, String partitionKey);

    /*
    Iterable<T> saveAll(@NonNull Iterable<T> entities, String dataPartitionId, String cosmosDBName, String collection);
    */

    Iterable<T> findAll(String dataPartitionId, String cosmosDBName, String collection);

    List<T> findAllById(Iterable<String> ids, String dataPartitionId, String cosmosDBName, String collection, String partitionKey);

    List<T> findByIds(Iterable<String> ids, String dataPartitionId, String cosmosDBName, String collection, String partitionKey);

    List<T> find(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query);

    T getOne(String dataPartitionId, String cosmosDBName, String collection, @NonNull String id, String partitionKey);
}

