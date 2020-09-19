package org.opengroup.osdu.storage.provider.azure.repository.interfaces;

import com.azure.cosmos.SqlQuerySpec;
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
