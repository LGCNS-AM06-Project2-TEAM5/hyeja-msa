package com.hyeja.engagementservice.policy.event;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * policy-updated 구독 전용 Kafka 설정입니다. Kafka 주소(spring.kafka.bootstrap-servers)가 있는 환경(Compose)에서만 켜집니다.
 * Cloud Bus(Kafka)가 같이 있으면 자동 설정 직렬화기가 바뀌므로 이 구독에 쓰는 Consumer·Producer는 String으로 직접 만듭니다.
 * 실패하면 1초 간격으로 3번 더 시도하고, 그래도 안 되거나 JSON이 깨졌으면 policy-updated-dlt로 보냅니다.
 */
@EnableKafka
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class PolicyEventKafkaConfig {
    public static final String TOPIC = "policy-updated";
    public static final String DLT = "policy-updated-dlt";
    public static final String GROUP_ID = "engagement-policy-snapshot";

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> policyUpdatedListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        var dltTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
        var recoverer = new DeadLetterPublishingRecoverer(dltTemplate, (record, e) -> new TopicPartition(DLT, -1));
        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        errorHandler.addNotRetryableExceptions(JacksonException.class);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
