package com.hyeja.engagementservice.member.event;

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
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class MemberEventKafkaConfig {

    public static final String TOPIC = "member-withdrawn";
    public static final String DLT = "member-withdrawn-dlt";
    public static final String GROUP_ID = "engagement-member-cleanup";
    public static final String DLT_GROUP_ID = "engagement-member-withdrawn-dlt-store";

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> memberWithdrawnListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        var recoverer = new DeadLetterPublishingRecoverer(
                stringTemplate(bootstrapServers),
                (record, exception) -> new TopicPartition(DLT, -1));
        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        errorHandler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory(bootstrapServers, GROUP_ID));
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> memberWithdrawnDltListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        // DLT 보관 DB가 일시적으로 실패해도 메시지를 버리지 않고 같은 offset을 계속 재시도합니다.
        var errorHandler = new DefaultErrorHandler(new FixedBackOff(5000L, Long.MAX_VALUE));
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory(bootstrapServers, DLT_GROUP_ID));
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    private DefaultKafkaConsumerFactory<String, String> consumerFactory(String bootstrapServers, String groupId) {
        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    private KafkaTemplate<String, String> stringTemplate(String bootstrapServers) {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
    }
}
