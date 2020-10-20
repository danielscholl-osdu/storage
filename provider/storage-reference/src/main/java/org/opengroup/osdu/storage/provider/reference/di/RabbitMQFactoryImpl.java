package org.opengroup.osdu.storage.provider.reference.di;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import javax.annotation.PostConstruct;
import org.opengroup.osdu.storage.provider.reference.messagebus.IMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Lazy
@Component
public class RabbitMQFactoryImpl implements IMessageFactory {

  private static final Logger LOG = LoggerFactory.getLogger(RabbitMQFactoryImpl.class);

  @Value("${mb.rabbitmq.uri}")
  private String uri;

  private Channel channel;

  @PostConstruct
  private void init() {
    ConnectionFactory factory = new ConnectionFactory();
    try {
      factory.setUri(uri);
      factory.setAutomaticRecoveryEnabled(true);
      Connection conn = factory.newConnection();
      this.channel = conn.createChannel();
      LOG.debug("RabbitMQ Channel was created.");
      for (String queue : Arrays.asList(DEFAULT_QUEUE_NAME, INDEXER_QUEUE_NAME, LEGAL_QUEUE_NAME)) {
        channel.queueDeclare("os-storage-" + queue, false, false, false, null);
        LOG.debug("Queue [os-storage-" + queue + "] was declared.");
      }
    } catch (KeyManagementException | NoSuchAlgorithmException | URISyntaxException | IOException | TimeoutException e) {
      LOG.error(e.getMessage(), e);
    }
  }

  @Override
  public void sendMessage(String msg) {
    sendMessage(DEFAULT_QUEUE_NAME, msg);
  }

  @Override
  public void sendMessage(String queueName, String msg) {
    String queueNameWithPrefix = "os-storage-" + queueName;
    try {
      channel.basicPublish("", queueNameWithPrefix, null, msg.getBytes());
      LOG.info(" [x] Sent '" + msg + "' to queue [" + queueNameWithPrefix + "]");
    } catch (IOException e) {
      LOG.error("Unable to publish message to [" + queueNameWithPrefix + "]");
      LOG.error(e.getMessage(), e);
    }
  }
}
