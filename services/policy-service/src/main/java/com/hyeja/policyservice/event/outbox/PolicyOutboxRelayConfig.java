package com.hyeja.policyservice.event.outbox;

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

// PolicyOutboxRelay를 주기적으로 돌립니다. Kafka 주소가 없는 환경(테스트·로컬)에서는 켜지지 않습니다.
// Relay가 쓰는 Producer는 여기서 직접 만듭니다. Cloud Bus(Kafka)가 같이 있으면 자동 설정 KafkaTemplate의
// key 직렬화기가 ByteArraySerializer가 되어 문자열 key를 보내지 못하기 때문입니다.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class PolicyOutboxRelayConfig {

    // KafkaTemplate을 빈으로 올리면 자동 설정 KafkaTemplate이 빠지므로, Relay 안에서만 씁니다.
    @Bean
    public PolicyOutboxRelay policyOutboxRelay(PolicyOutboxRepository policyOutboxRepository, Clock clock,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                // Kafka가 내려가 있으면 기본 60초 동안 막히므로 전송 대기 시간과 맞춥니다.
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        return new PolicyOutboxRelay(policyOutboxRepository, kafkaTemplate, clock);
    }
}
