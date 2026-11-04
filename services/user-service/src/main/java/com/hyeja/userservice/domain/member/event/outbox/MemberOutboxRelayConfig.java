package com.hyeja.userservice.domain.member.event.outbox;

import java.time.Clock;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

// Kafka 주소가 있는 환경에서만 Relay를 활성화합니다.
// Cloud Bus의 자동 설정 KafkaTemplate과 분리해 key와 value를 모두 문자열로 직렬화합니다.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class MemberOutboxRelayConfig {

    @Bean(destroyMethod = "close")
    public MemberOutboxRelay memberOutboxRelay(
            MemberOutboxRepository memberOutboxRepository,
            Clock clock,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> properties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000
        );
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(properties)
        );
        return new MemberOutboxRelay(memberOutboxRepository, kafkaTemplate, clock);
    }

    @Bean
    @ConditionalOnProperty(
            name = "member.outbox.relay-scheduling-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public MemberOutboxRelayScheduler memberOutboxRelayScheduler(MemberOutboxRelay relay) {
        return new MemberOutboxRelayScheduler(relay);
    }
}
