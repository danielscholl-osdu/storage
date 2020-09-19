package org.opengroup.osdu.storage.provider.azure.repository;

import com.azure.cosmos.ConflictException;
import com.azure.cosmos.CosmosClientException;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosItem;
import com.azure.cosmos.CosmosItemProperties;
import com.azure.cosmos.CosmosItemRequestOptions;
import com.azure.cosmos.FeedOptions;
import com.azure.cosmos.FeedResponse;
import com.azure.cosmos.NotFoundException;
import com.azure.cosmos.SqlQuerySpec;
import com.azure.cosmos.internal.AsyncDocumentClient;
import com.azure.cosmos.internal.Document;
import org.opengroup.osdu.azure.ICosmosClientFactory;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.provider.azure.query.CosmosStorePageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simpler interface for interacting with CosmosDB.
 * Usage Examples:
 * <pre>
 * {@code
 *      @Inject
 *      private CosmosContainer container;
 *
 *      @Inject
 *      private CosmosStore cosmosStore;
 *
 *      void findItemExample() {
 *          Optional<MyObject> myItem = cosmosStore.findItem("dataPartitionId", "cosmosDb", "collection", "id", "partition-key", MyObject.class);
 *          myItem.isPresent(); // true if found, false otherwise
 *      }
 *
 *      void findAllItemsExample() {
 *          List<MyObject> objects = cosmosStore.findAllItems("dataPartitionId", "cosmosDb", "collection", MyObject.class);
 *      }
 *
 *      void queryItemsExample() {
 *          SqlQuerySpec query = new SqlQuerySpec()
 *                 .setQueryText("SELECT * FROM c WHERE c.isFoo = @isFoo")
 *                 .setParameters(new SqlParameterList(new SqlParameter("@isFoo", true)));
 *         FeedOptions options = new FeedOptions().setEnableCrossPartitionQuery(true);
 *
 *         List<MyObject> objects = cosmosStore.queryItems("dataPartitionId", "cosmosDb", "collection", query, options, MyObject.class);
 *      }
 *
 *      void createItemExample() {
 *          cosmosStore.createItem("dataPartitionId", "cosmosDb", "collection", "some-data");
 *      }
 * }
 * </pre>
 */

@Component
@Lazy
public class AdvancedCosmosStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedCosmosStore.class.getName());

    @Autowired
    private ICosmosClientFactory cosmosClientFactory;

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param id              ID of item
     * @param partitionKey    Partition key of item
     */
    public void deleteItem(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final String id,
            final String partitionKey) {
        try {
            CosmosContainer cosmosContainer = getCosmosContainer(dataPartitionId, cosmosDBName, collection);
            findItem(cosmosContainer, id, partitionKey).delete(new CosmosItemRequestOptions(partitionKey));
        } catch (NotFoundException e) {
            String errorMessage = "Item was unexpectedly not found";
            LOGGER.warn(errorMessage, e);
            throw new AppException(404, errorMessage, e.getMessage(), e);
        } catch (CosmosClientException e) {
            String errorMessage = "Unexpectedly failed to delete item from CosmosDB";
            LOGGER.warn(errorMessage, e);
            throw new AppException(500, errorMessage, e.getMessage(), e);
        }
    }

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param id              ID of item
     * @param partitionKey    Partition key of item
     * @param clazz           Class to serialize results into
     * @param <T>             Type to return
     * @return The item that was found based on the IDs provided
     */
    public <T> Optional<T> findItem(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final String id,
            final String partitionKey,
            final Class<T> clazz) {
        try {
            CosmosContainer cosmosContainer = getCosmosContainer(dataPartitionId, cosmosDBName, collection);
            T item = findItem(cosmosContainer, id, partitionKey)
                    .read(new CosmosItemRequestOptions(partitionKey))
                    .getProperties()
                    .getObject(clazz);
            return Optional.ofNullable(item);
        } catch (NotFoundException e) {
            LOGGER.warn(String.format("Unable to find item with ID=%s and PK=%s", id, partitionKey));
            return Optional.empty();
        } catch (IOException | CosmosClientException e) {
            String errorMessage;
            if (e instanceof IOException) {
                errorMessage = String.format("Malformed document for item with ID=%s and PK=%s", id, partitionKey);
            } else {
                errorMessage = "Unexpectedly encountered error calling CosmosDB";
            }
            LOGGER.warn(errorMessage, e);
            throw new AppException(500, errorMessage, e.getMessage(), e);
        }
    }

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param clazz           Class type of response
     * @param <T>             Type of response
     * @return List of items found in container
     */
    public <T> List<T> findAllItems(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final Class<T> clazz) {
        FeedOptions options = new FeedOptions().setEnableCrossPartitionQuery(true);
        return queryItems(dataPartitionId, cosmosDBName, collection, new SqlQuerySpec("SELECT * FROM c"), options, clazz);
    }

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param query           {@link SqlQuerySpec} to execute
     * @param options         Query options
     * @param clazz           Class type of response
     * @param <T>             Type of response
     * @return List of items found in container
     */
    public <T> List<T> queryItems(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final SqlQuerySpec query,
            final FeedOptions options,
            final Class<T> clazz) {
        ArrayList<T> results = new ArrayList<>();
        CosmosContainer cosmosContainer = getCosmosContainer(dataPartitionId, cosmosDBName, collection);
        Iterator<FeedResponse<CosmosItemProperties>> paginatedResponse = cosmosContainer.queryItems(query, options);
        while (paginatedResponse.hasNext()) {
            for (CosmosItemProperties properties : paginatedResponse.next().getResults()) {
                try {
                    results.add(properties.getObject(clazz));
                } catch (IOException e) {
                    String errorMessage = String.format("Malformed document for item with ID=%s", properties.getId());
                    LOGGER.warn(errorMessage, e);
                    throw new AppException(500, errorMessage, e.getMessage(), e);
                }
            }
        }
        return results;
    }

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param clazz           Class type of response
     * @param pageSize        Number of items returned
     * @param pageNum         Page number returned
     * @param <T>             Type of response
     * @return List of items found on specific page in container
     */
    public <T> List<T> findAllItemsAsync(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final Class<T> clazz,
            final short pageSize,
            final int pageNum) {
        return queryItemsAsync(dataPartitionId, cosmosDBName, collection, new SqlQuerySpec("SELECT * FROM c"), clazz, pageSize, pageNum);
    }

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param clazz           Class type of response
     * @param pageSize        Number of items returned
     * @param pageNum         Page number returned
     * @param <T>             Type of response
     * @return Specific page found in container
     */
    public <T> Page<T> findAllItemsAsyncPage(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final Class<T> clazz,
            final short pageSize,
            final int pageNum) {
        return queryItemsAsyncPage(dataPartitionId, cosmosDBName, collection, new SqlQuerySpec("SELECT * FROM c"), clazz, pageSize, pageNum);
    }

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param query           {@link SqlQuerySpec} to execute
     * @param clazz           Class type of response
     * @param pageSize        Number of items returned
     * @param pageNum         Page number returned
     * @param <T>             Type of response
     * @return List of items found on specific page in container
     */
    public <T> List<T> queryItemsAsync(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final SqlQuerySpec query,
            final Class<T> clazz,
            final short pageSize,
            final int pageNum) {
        String continuationToken = null;
        int currentPage = 0;
        HashMap<String, List<T>> results;
        do {
            String nextContinuationToken = "";
            AsyncDocumentClient client = cosmosClientFactory.getAsyncClient(dataPartitionId);
            results = returnItemsWithToken(client, cosmosDBName, collection, query, clazz, pageSize, continuationToken);
            for (Map.Entry<String, List<T>> entry : results.entrySet()) {
                nextContinuationToken = entry.getKey();
            }
            continuationToken = nextContinuationToken;
            currentPage++;

        } while (currentPage < pageNum && continuationToken != null);
        return results.get(continuationToken);
    }

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param query           {@link SqlQuerySpec} to execute
     * @param clazz           Class type of response
     * @param pageSize        Number of items returned
     * @param pageNum         Page number returned
     * @param <T>             Type of response
     * @return Specific Page of items found on specific page in container
     */
    public <T> Page<T> queryItemsAsyncPage(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final SqlQuerySpec query,
            final Class<T> clazz,
            final short pageSize,
            final int pageNum) {
        String continuationToken = null;
        int currentPage = 0;
        HashMap<String, List<T>> results;
        do {
            String nextContinuationToken = "";
            AsyncDocumentClient client = cosmosClientFactory.getAsyncClient(dataPartitionId);
            results = returnItemsWithToken(client, cosmosDBName, collection, query, clazz, pageSize, continuationToken);
            for (Map.Entry<String, List<T>> entry : results.entrySet()) {
                nextContinuationToken = entry.getKey();
            }
            continuationToken = nextContinuationToken;
            currentPage++;

        } while (currentPage < pageNum && continuationToken != null);

        CosmosStorePageRequest pageRequest = CosmosStorePageRequest.of(pageNum, pageSize, continuationToken);
        return new PageImpl(results.get(continuationToken), pageRequest, 0);
    }


    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param query           {@link SqlQuerySpec} to execute
     * @param clazz           Class type of response
     * @param <T>             Type of response
     * @return Specific Page of items found in container
     */
    public <T> Page<T> queryItemsAsyncPage(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final SqlQuerySpec query,
            final Class<T> clazz,
            final int pageSize,
            final String continuationToken) {

        HashMap<String, List<T>> results;
        String internalContinuationToken = continuationToken;

        String nextContinuationToken = null;
        AsyncDocumentClient client = cosmosClientFactory.getAsyncClient(dataPartitionId);
        results = returnItemsWithContinuationToken(client, cosmosDBName, collection, query, clazz, pageSize, internalContinuationToken);
        for (Map.Entry<String, List<T>> entry : results.entrySet()) {
            nextContinuationToken = entry.getKey();
        }
        internalContinuationToken = nextContinuationToken;

        CosmosStorePageRequest pageRequest = CosmosStorePageRequest.of(0, pageSize, internalContinuationToken);

        return new PageImpl(results.get(internalContinuationToken), pageRequest, 0);
    }


    /**
     * @param client            {@link AsyncDocumentClient} used to configure/execute requests against database service
     * @param dbName            Cosmos DB name
     * @param container         Container to query
     * @param query             {@link SqlQuerySpec} to execute
     * @param clazz             Class type of response
     * @param <T>               Type of response
     * @param pageSize          Number of items returned
     * @param continuationToken Token used to continue the enumeration
     * @return Continuation Token and list of documents in container
     */
    private static <T> HashMap<String, List<T>> returnItemsWithContinuationToken(
            final AsyncDocumentClient client,
            final String dbName,
            final String container,
            final SqlQuerySpec query,
            final Class<T> clazz,
            final int pageSize,
            final String continuationToken) {

        HashMap<String, List<T>> map = new HashMap<>();
        List<T> items = new ArrayList<T>();

        FeedOptions feedOptions = new FeedOptions()
                .maxItemCount((int) pageSize)
                .setEnableCrossPartitionQuery(true)
                .requestContinuation(continuationToken);

        String collectionLink = String.format("/dbs/%s/colls/%s", dbName, container);
        Flux<FeedResponse<Document>> queryFlux = client.queryDocuments(collectionLink, query, feedOptions);

        Iterator<FeedResponse<Document>> it = queryFlux.toIterable().iterator();

        FeedResponse<Document> page = it.next();
        List<Document> results = page.getResults();
        for (Document doc : results) {
            T obj = doc.toObject(clazz);
            items.add(obj);
        }
        map.put(page.getContinuationToken(), items);
        return map;
    }

    /**
     * @param client            {@link AsyncDocumentClient} used to configure/execute requests against database service
     * @param dbName            Cosmos DB name
     * @param container         Container to query
     * @param query             {@link SqlQuerySpec} to execute
     * @param clazz             Class type of response
     * @param <T>               Type of response
     * @param pageSize          Number of items returned
     * @param continuationToken Token used to continue the enumeration
     * @return Continuation Token and list of documents in container
     */
    private static <T> HashMap<String, List<T>> returnItemsWithToken(
            final AsyncDocumentClient client,
            final String dbName,
            final String container,
            final SqlQuerySpec query,
            final Class<T> clazz,
            final short pageSize,
            final String continuationToken) {

        HashMap<String, List<T>> map = new HashMap<>();
        List<T> items = new ArrayList<T>();

        FeedOptions feedOptions = new FeedOptions()
                .maxItemCount((int) pageSize)
                .setEnableCrossPartitionQuery(true)
                .requestContinuation(continuationToken);

        String collectionLink = String.format("/dbs/%s/colls/%s", dbName, container);
        Flux<FeedResponse<Document>> queryFlux = client.queryDocuments(collectionLink, query, feedOptions);

        Iterator<FeedResponse<Document>> it = queryFlux.toIterable().iterator();

        FeedResponse<Document> page = it.next();
        List<Document> results = page.getResults();
        for (Document doc : results) {
            T obj = doc.toObject(clazz);
            items.add(obj);
        }

        map.put(page.getContinuationToken(), items);
        return map;
    }

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param item            Data object to store
     * @param <T>             Type of response
     */
    public <T> void upsertItem(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final T item) {
        try {
            CosmosContainer cosmosContainer = getCosmosContainer(dataPartitionId, cosmosDBName, collection);
            cosmosContainer.upsertItem(item);
        } catch (CosmosClientException e) {
            String errorMessage = "Unexpectedly failed to put item into CosmosDB";
            LOGGER.warn(errorMessage, e);
            throw new AppException(500, errorMessage, e.getMessage(), e);
        }
    }

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @param item            Data object to store
     * @param <T>             Type of response
     */
    public <T> void createItem(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection,
            final T item) {
        try {
            CosmosContainer cosmosContainer = getCosmosContainer(dataPartitionId, cosmosDBName, collection);
            cosmosContainer.createItem(item);
        } catch (ConflictException e) {
            String errorMessage = "Resource with specified id or name already exists.";
            LOGGER.warn(errorMessage, e);
            throw new AppException(409, errorMessage, e.getMessage(), e);
        } catch (CosmosClientException e) {
            String errorMessage = "Unexpectedly failed to insert item into CosmosDB";
            LOGGER.warn(errorMessage, e);
            throw new AppException(500, errorMessage, e.getMessage(), e);
        }
    }

    /**
     * @param cosmos       Container to query
     * @param id           ID of item
     * @param partitionKey Partition key of item
     * @return The item. It may not exist - the caller must check
     */
    private static CosmosItem findItem(
            final CosmosContainer cosmos,
            final String id,
            final String partitionKey) {
        return cosmos.getItem(id, partitionKey);
    }

    /**
     * @param dataPartitionId Data partition id to fetch appropriate cosmos client for each partition
     * @param cosmosDBName    Database to be used
     * @param collection      Collection to be used
     * @return Cosmos container
     */
    private CosmosContainer getCosmosContainer(
            final String dataPartitionId,
            final String cosmosDBName,
            final String collection) {
        try {
            return cosmosClientFactory.getClient(dataPartitionId)
                    .getDatabase(cosmosDBName)
                    .getContainer(collection);
        } catch (Exception e) {
            String errorMessage = "Error creating creating Cosmos Client";
            LOGGER.warn(errorMessage, e);
            throw new AppException(500, errorMessage, e.getMessage(), e);
        }

    }
}
