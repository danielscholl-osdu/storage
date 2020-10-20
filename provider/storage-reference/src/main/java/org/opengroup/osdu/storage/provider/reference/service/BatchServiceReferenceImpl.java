package org.opengroup.osdu.storage.provider.reference.service;

import static java.util.Collections.singletonList;

import javax.inject.Inject;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.opengroup.osdu.storage.service.BatchServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BatchServiceReferenceImpl extends BatchServiceImpl {

  @Inject
  private StorageAuditLogger auditLogger;

  @Inject
  private IQueryRepository queryRepository;

  @Override
  public DatastoreQueryResult getAllKinds(String cursor, Integer limit) {
    DatastoreQueryResult result = this.queryRepository.getAllKinds(limit, cursor);
    this.auditLogger.readAllKindsSuccess(result.getResults());
    return result;
  }

  @Override
  public DatastoreQueryResult getAllRecords(String cursor, String kind, Integer limit) {
    DatastoreQueryResult result = this.queryRepository.getAllRecordIdsFromKind(kind, limit, cursor);
    if (!result.getResults().isEmpty()) {
      this.auditLogger.readAllRecordsOfGivenKindSuccess(singletonList(kind));
    }
    return result;
  }

}