package com.hyeja.engagementservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hyeja.engagementservice.policy.event.PolicyEventKafkaConfig;
import com.hyeja.engagementservice.policy.event.PolicyEventMetrics;
import com.hyeja.engagementservice.policy.event.ProcessedPolicyEventRepository;
import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

/** 내장 Kafka로 policy-updated를 보내 policy_snapshot 반영, 같은 eventId 중복 무시, 깨진 메시지의 DLT 이동을 봅니다. */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {PolicyEventKafkaConfig.TOPIC, PolicyEventKafkaConfig.DLT})
@ActiveProfiles("test")
class PolicyUpdatedKafkaIntegrationTest {
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired PolicyRepository policies;
    @Autowired ProcessedPolicyEventRepository processed;
    @Autowired MeterRegistry meterRegistry;

    @AfterEach void cleanUp() {
        processed.deleteAll();
        policies.deleteAll();
    }

    @Test void 이벤트를_받으면_스냅샷에_반영하고_같은_eventId는_한_번만_반영한다() {
        double appliedBefore = count(PolicyEventMetrics.EVENTS, "result", "applied");
        double duplicateBefore = count(PolicyEventMetrics.EVENTS, "result", "duplicate");
        KafkaTemplate<String, String> producer = producer();
        producer.send(PolicyEventKafkaConfig.TOPIC, "K1", event("e-1", "K1", "청년 월세"));
        producer.send(PolicyEventKafkaConfig.TOPIC, "K1", event("e-1", "K1", "다시 온 같은 이벤트"));
        producer.send(PolicyEventKafkaConfig.TOPIC, "K2", event("e-2", "K2", "다음 정책"));

        // 파티션이 하나라 e-2가 반영됐으면 앞의 두 메시지도 이미 처리된 뒤입니다.
        await().atMost(Duration.ofSeconds(20)).until(() -> policies.existsById("K2"));
        assertThat(policies.findById("K1").orElseThrow().getPolicyName()).isEqualTo("청년 월세");
        assertThat(processed.count()).isEqualTo(2);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(count(PolicyEventMetrics.EVENTS, "result", "applied") - appliedBefore).isEqualTo(2);
            assertThat(count(PolicyEventMetrics.EVENTS, "result", "duplicate") - duplicateBefore).isEqualTo(1);
        });
    }

    @Test void JSON이_깨진_메시지는_재시도_없이_DLT로_보낸다() {
        double dltBefore = count(PolicyEventMetrics.DLT, null, null);
        producer().send(PolicyEventKafkaConfig.TOPIC, "BAD", "{not-json");

        try (Consumer<String, String> consumer = consumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, PolicyEventKafkaConfig.DLT);
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, PolicyEventKafkaConfig.DLT, Duration.ofSeconds(20));
            assertThat(record.value()).isEqualTo("{not-json");
        }
        assertThat(processed.count()).isZero();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(count(PolicyEventMetrics.DLT, null, null) - dltBefore).isEqualTo(1));
    }

    // 대시보드가 Prometheus에서 읽는 값과 같은 카운터입니다. 아직 한 번도 안 올랐으면 0으로 봅니다.
    private double count(String name, String tagKey, String tagValue) {
        var search = meterRegistry.find(name);
        if (tagKey != null) {
            search = search.tag(tagKey, tagValue);
        }
        var counter = search.counter();
        return counter == null ? 0 : counter.count();
    }

    private KafkaTemplate<String, String> producer() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
    }

    private Consumer<String, String> consumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "dlt-check", false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }

    private static String event(String eventId, String policyId, String name) {
        return """
                {"eventId":"%s","eventType":"policy-updated","eventVersion":1,"occurredAt":"2026-11-03T17:00:00+09:00",
                 "aggregateId":"%s","policyId":"%s","policyName":"%s","categories":["MONTHLY_RENT"],
                 "supportContent":"월 20만 원","applyEndDate":"2026-12-31","applyPeriodCode":"SPECIFIC_PERIOD",
                 "applyUrl":"https://example.com/apply","activeYn":true,"deleted":false}
                """.formatted(eventId, policyId, policyId, name);
    }
}
