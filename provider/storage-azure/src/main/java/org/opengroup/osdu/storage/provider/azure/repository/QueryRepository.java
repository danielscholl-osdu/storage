package org.opengroup.osdu.storage.provider.azure.repository;

import com.azure.data.cosmos.CosmosClientException;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.provider.azure.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.azure.SchemaDoc;
import org.opengroup.osdu.storage.provider.azure.query.CosmosStorePageRequest;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

@Repository
public class QueryRepository implements IQueryRepository {

    @Autowired
    private RecordMetadataRepository record;

    @Autowired
    private SchemaRepository schema;

    @Autowired
    private JaxRsDpsLog logger;

    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {

        boolean paginated  = false;

        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
            paginated = true;
        }

        if (cursor != null && !cursor.isEmpty()) {
            paginated = true;
        }

        Sort sort = Sort.by(Sort.Direction.ASC, "kind");
         DatastoreQueryResult dqr = new DatastoreQueryResult();
        List<String> kinds = new ArrayList();
        Iterable<SchemaDoc> docs;

        try {
            if (paginated) {
                final Page<SchemaDoc> docPage = schema.findAll(CosmosStorePageRequest.of(0, numRecords, cursor, sort));
                Pageable pageable = docPage.getPageable();
                String continuation = null;
                if (pageable instanceof CosmosStorePageRequest) {
                    continuation = ((CosmosStorePageRequest) pageable).getRequestContinuation();
                }
                dqr.setCursor(continuation);
                docs = docPage.getContent();
            } else {
                docs = schema.findAll(sort);
            }
            docs.forEach(
                    d -> kinds.add(d.getKind()));
            dqr.setResults(kinds);
        } catch (Exception e) {
            if (e.getMessage().contains("Invalid Continuation Token"))
                throw this.getInvalidCursorException();
        }
        //for (String id : dqr.getResults())
        //    System.out.println("getAllKinds ID="+id);
        return dqr;

    }

    @Override
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor) {
        Assert.notNull(kind, "kind must not be null");

        boolean paginated  = false;

        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
            paginated = true;
        }

        if (cursor != null && !cursor.isEmpty()) {
            paginated = true;
        }

        String status = RecordState.active.toString();
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        DatastoreQueryResult dqr = new DatastoreQueryResult();
        List<String> ids = new ArrayList();
        Iterable<RecordMetadataDoc> docs;

        try {
            if (paginated) {
                final Page<RecordMetadataDoc> docPage = record.findByMetadata_kindAndMetadata_status(kind, status,
                        CosmosStorePageRequest.of(0, numRecords, cursor, sort));
                Pageable pageable = docPage.getPageable();
                String continuation = null;
                if (pageable instanceof CosmosStorePageRequest) {
                    continuation = ((CosmosStorePageRequest) pageable).getRequestContinuation();
                }
                dqr.setCursor(continuation);
                docs = docPage.getContent();
            } else {
                docs = record.findByMetadata_kindAndMetadata_status(kind, status);
            }
            docs.forEach(d -> ids.add(d.getId()));
            dqr.setResults(ids);
        } catch (Exception e) {
            if (e.getMessage().contains("Invalid Continuation Token"))
                throw this.getInvalidCursorException();
        }
        //for (String id : dqr.getResults())
        //    System.out.println("getAllRecordIdsFromKind ID="+id);
        return dqr;
    }


    private AppException getInvalidCursorException() {
        return new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid",
                "The requested cursor does not exist or is invalid");
    }}
