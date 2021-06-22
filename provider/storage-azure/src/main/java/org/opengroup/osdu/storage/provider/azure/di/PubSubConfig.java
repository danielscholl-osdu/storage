package org.opengroup.osdu.storage.provider.azure.di;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.inject.Named;

@Configuration
public class PubSubConfig {
    @Value("${azure.servicebus.topic-name}")
    private String serviceBusTopic;

    @Value("${azure.legal.servicebus.topic-name}")
    private String legalServiceBusTopic;

    @Value("${azure.legal.servicebus.topic-subscription}")
    private String legalServiceBusTopicSubscription;

    @Bean
    @Named("SERVICE_BUS_TOPIC")
    public String serviceBusTopic() {
        return serviceBusTopic;
    }

    @Bean
    @Named("LEGAL_SERVICE_BUS_TOPIC")
    public String legalServiceBusTopic() {
        return legalServiceBusTopic;
    }

    @Bean
    @Named("LEGAL_SERVICE_BUS_TOPIC_SUBSCRIPTION")
    public String legalServiceBusTopicSubscription() {
        return legalServiceBusTopicSubscription;
    }
}
