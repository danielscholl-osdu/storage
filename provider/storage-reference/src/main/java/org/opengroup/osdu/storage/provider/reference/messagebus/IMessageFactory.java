package org.opengroup.osdu.storage.provider.reference.messagebus;

public interface IMessageFactory {

  String DEFAULT_QUEUE_NAME = "records";
  String LEGAL_QUEUE_NAME = "legal";
  String INDEXER_QUEUE_NAME = "indexer";

  void sendMessage(String msg);

  void sendMessage(String queueName, String msg);

}
