/*
  Copyright 2020 Google LLC
  Copyright 2020 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp;

import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PubsubMessage.Builder;
import java.util.Objects;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.threeten.bp.Duration;
import org.opengroup.osdu.core.common.model.http.AppException;
import java.util.Arrays;

@Component
public class GooglePubSub implements IMessageBus {

	@Value("${PUBSUB_SEARCH_TOPIC}")
	public String pubsubSearchTopic;

	private Publisher publisher;


	private static final RetrySettings RETRY_SETTINGS = RetrySettings.newBuilder()
			.setTotalTimeout(Duration.ofSeconds(10))
			.setInitialRetryDelay(Duration.ofMillis(5))
			.setRetryDelayMultiplier(2)
			.setMaxRetryDelay(Duration.ofSeconds(3))
			.setInitialRpcTimeout(Duration.ofSeconds(10))
			.setRpcTimeoutMultiplier(2)
			.setMaxRpcTimeout(Duration.ofSeconds(10))
			.build();

	@Autowired
	private TenantInfo tenant;

	@Override
	public void publishMessage(DpsHeaders headers, PubSubInfo... messages) {

		if(Objects.isNull(publisher)) {
			try {
				publisher = Publisher.newBuilder(
					ProjectTopicName.newBuilder()
						.setProject(this.tenant.getProjectId())
						.setTopic(pubsubSearchTopic).build())
					.setRetrySettings(RETRY_SETTINGS).build();
			} catch (Exception e) {
				throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal error",
					"A fatal internal error has occurred.", e);
			}
		}

		final int BATCH_SIZE = 50;

		for (int i = 0; i < messages.length; i += BATCH_SIZE) {
			PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));

			String json = new Gson().toJson(batch);
			ByteString data = ByteString.copyFromUtf8(json);

			Builder messageBuilder = PubsubMessage.newBuilder();
			messageBuilder.putAttributes(DpsHeaders.ACCOUNT_ID, this.tenant.getName());
			messageBuilder.putAttributes(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
			headers.addCorrelationIdIfMissing();
			messageBuilder.putAttributes(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
			messageBuilder.setData(data);

			publisher.publish(messageBuilder.build());
		}
	}
}