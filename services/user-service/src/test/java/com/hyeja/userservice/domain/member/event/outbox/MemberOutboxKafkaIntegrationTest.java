package com.hyeja.userservice.domain.member.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyeja.userservice.domain.member.event.MemberWithdrawnPublisher;
import java.util.Map;
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

// Outbox 저장부터 Kafka member-withdrawn 토픽 전송까지 내장 브로커로 검증합니다.
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "member.outbox.relay-scheduling-enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = "member-withdrawn")
@ActiveProfiles("test")
class MemberOutboxKafkaIntegrationTest {

    @Autowired private MemberWithdrawnPublisher publisher;
    @Autowired private MemberOutboxRelay relay;
    @Autowired private MemberOutboxRepository outbox;
    @Autowired private TransactionTemplate tx;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private EmbeddedKafkaBroker broker;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
    }

    @Test
    void 저장한_탈퇴_이벤트가_memberId를_key로_토픽에_한_번_나간다() {
        tx.executeWithoutResult(status -> publisher.publish(1L));

        assertThat(relay.relayPending()).isEqualTo(1);
        assertThat(relay.relayPending()).isZero();
        assertThat(outbox.findAll()).allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());

        try (Consumer<String, String> consumer = consumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "member-withdrawn");
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "member-withdrawn");

            assertThat(record.key()).isEqualTo("1");
            JsonNode event = jsonMapper.readTree(record.value());
            assertThat(event.get("eventId").asString()).isEqualTo(outbox.findAll().get(0).getEventId());
            assertThat(event.get("eventType").asString()).isEqualTo("member-withdrawn");
            assertThat(event.get("memberId").asLong()).isEqualTo(1L);
        }
    }

    private Consumer<String, String> consumer() {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(broker, "member-outbox-test", false);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<String, String>(properties).createConsumer();
    }
}
