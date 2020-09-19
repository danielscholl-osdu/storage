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
        System.out.println(sort.toString());
        DatastoreQueryResult dqr = new DatastoreQueryResult();
        List<String> kinds = new ArrayList();
        Iterable<SchemaDoc> docs;

        try {
            if (paginated) {
                System.out.println(" getAllKinds(Integer limit, String cursor) ERIK PAGINATED=" + paginated);
                final Page<SchemaDoc> docPage = schema.findAll(CosmosStorePageRequest.of(0, numRecords, cursor, sort));
                System.out.println(" 01getAllKinds(Integer limit, String cursor) ERIK PAGINATED=" + paginated);
                Pageable pageable = docPage.getPageable();
                System.out.println(" 1getAllKinds(Integer limit, String cursor) ERIK PAGINATED=" + paginated);
                String continuation = ((CosmosStorePageRequest) pageable).getRequestContinuation();
                System.out.println(" 2getAllKinds(Integer limit, String cursor) ERIK PAGINATED=" + paginated);
                dqr.setCursor(continuation);
                System.out.println(" 3getAllKinds(Integer limit, String cursor) ERIK PAGINATED=" + paginated);
                docs = docPage.getContent();
                System.out.println(" 4getAllKinds(Integer limit, String cursor) ERIK PAGINATED=" + paginated);
            } else {
                System.out.println(" getAllKinds(Integer limit, String cursor) ERIK NOT PAGINATED=" + paginated);
                docs = schema.findAll(sort);
            }
            docs.forEach(
                    d -> kinds.add(d.getKind()));
            System.out.println(kinds.size());
            System.out.println(" getAllKinds(Integer limit, String cursor) getAllKinds PAGE=" + dqr.getCursor());
            dqr.setResults(kinds);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            if (e.getCause() instanceof CosmosClientException) {
                CosmosClientException ce = (CosmosClientException) e.getCause();
                if(ce.statusCode() == HttpStatus.SC_BAD_REQUEST && ce.getMessage().contains("Invalid Continuation Token"))
                    throw this.getInvalidCursorException();
            }
        }

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
                System.out.println("getAllRecordIdsFromKind paginated=" + paginated);
                final Page<RecordMetadataDoc> docPage = record.findByMetadata_kindAndMetadata_status(kind, status,
                        CosmosStorePageRequest.of(0, numRecords, cursor, sort));
                Pageable pageable = docPage.getPageable();
                String continuation = ((CosmosStorePageRequest) pageable).getRequestContinuation();
                dqr.setCursor(continuation);
                docs = docPage.getContent();
            } else {
                System.out.println("getAllRecordIdsFromKind paginated=" + paginated);
                docs = record.findByMetadata_kindAndMetadata_status(kind, status);
            }
            docs.forEach(d -> ids.add(d.getId()));
            dqr.setResults(ids);
            System.out.println("getAllRecordIdsFromKind PAGE=" + dqr.getCursor());
        } catch (Exception e) {
            System.out.println("EXCEPTION!!!=" + e.getMessage() + " " + e.getCause() + " " + e.getStackTrace());
            if (e.getCause() instanceof CosmosClientException) {
                CosmosClientException ce = (CosmosClientException) e.getCause();
                if(ce.statusCode() == HttpStatus.SC_BAD_REQUEST && ce.getMessage().contains("Invalid Continuation Token"))
                    throw this.getInvalidCursorException();
            }
        }

        return dqr;
    }


    private AppException getInvalidCursorException() {
        return new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid",
                "The requested cursor does not exist or is invalid");
    }}
