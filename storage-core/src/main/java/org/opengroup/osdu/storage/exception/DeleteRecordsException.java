package org.opengroup.osdu.storage.exception;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

public class DeleteRecordsException extends RuntimeException {

  private final List<Pair<String, String>> notDeletedRecords;

  public DeleteRecordsException(List<Pair<String, String>>  notDeletedRecords) {
    this.notDeletedRecords = notDeletedRecords;
  }

  public List<Pair<String, String>> getNotDeletedRecords() {
    return notDeletedRecords;
  }
}
