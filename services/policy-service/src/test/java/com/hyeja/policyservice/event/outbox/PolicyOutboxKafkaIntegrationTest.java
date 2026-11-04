package com.hyeja.policyservice.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyeja.policyservice.event.PolicyChangedPublisher;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 정책 저장 → policy_outbox → Relay → Kafka policy-updated 토픽까지 실제 브로커(내장 Kafka)로 이어지는지 봅니다.
// 주기 실행은 길게 미뤄 두고 relayPending()을 직접 불러, 테스트가 보내는 시점을 정합니다.
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "policy.outbox.relay-interval-ms=600000"})
@EmbeddedKafka(partitions = 1, topics = "policy-updated")
@ActiveProfiles("test")
class PolicyOutboxKafkaIntegrationTest {

    @Autowired private PolicyChangedPublisher publisher;
    @Autowired private PolicyOutboxRelay relay;
    @Autowired private PolicyRepository policies;
    @Autowired private PolicyOutboxRepository outbox;
    @Autowired private TransactionTemplate tx;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private EmbeddedKafkaBroker broker;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        policies.deleteAllById(List.of("kafka-1"));
    }

    @Test
    void 저장한_정책이_policyId를_key로_토픽에_한_번_나간다() {
        tx.executeWithoutResult(status -> {
            policies.save(Policy.builder().policyId("kafka-1").policyName("청년 전세 kafka-1")
                    .categories(Set.of(PolicyCategory.JEONSE))
                    .ageLimitYn(false)
                    .houselessRequirement(PolicyHouselessRequirement.REQUIRED)
                    .applyPeriodCode(PolicyApplyPeriod.ALWAYS)
                    .viewCount(0).activeYn(true).build());
            publisher.publish(List.of("kafka-1"));
        });

        assertThat(relay.relayPending()).isEqualTo(1);
        assertThat(relay.relayPending()).isZero();
        assertThat(outbox.findAll()).allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());

        try (Consumer<String, String> consumer = consumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "policy-updated");
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "policy-updated");

            assertThat(record.key()).isEqualTo("kafka-1");
            JsonNode event = jsonMapper.readTree(record.value());
            assertThat(event.get("eventId").asString()).isEqualTo(outbox.findAll().get(0).getEventId());
            assertThat(event.get("policyName").asString()).isEqualTo("청년 전세 kafka-1");
            assertThat(event.get("applyPeriodCode").asString()).isEqualTo("ALWAYS");
        }
    }

    private Consumer<String, String> consumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("policy-outbox-test", "false", broker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}
