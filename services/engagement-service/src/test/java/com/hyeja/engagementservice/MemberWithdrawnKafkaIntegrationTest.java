package com.hyeja.engagementservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hyeja.engagementservice.favorite.entity.Favorite;
import com.hyeja.engagementservice.favorite.repository.FavoriteRepository;
import com.hyeja.engagementservice.member.event.FailedMemberEventRepository;
import com.hyeja.engagementservice.member.event.MemberEventKafkaConfig;
import com.hyeja.engagementservice.member.event.ProcessedMemberEventRepository;
import com.hyeja.engagementservice.notification.entity.Notification;
import com.hyeja.engagementservice.notification.repository.NotificationRepository;
import com.hyeja.engagementservice.policy.entity.Policy;
import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {MemberEventKafkaConfig.TOPIC, MemberEventKafkaConfig.DLT})
@ActiveProfiles("test")
class MemberWithdrawnKafkaIntegrationTest {

    @Autowired EmbeddedKafkaBroker broker;
    @Autowired FavoriteRepository favorites;
    @Autowired NotificationRepository notifications;
    @Autowired PolicyRepository policies;
    @Autowired FailedMemberEventRepository failedEvents;
    @Autowired ProcessedMemberEventRepository processedEvents;

    @AfterEach
    void cleanUp() {
        notifications.deleteAll();
        favorites.deleteAll();
        failedEvents.deleteAll();
        processedEvents.deleteAll();
        policies.deleteAll();
    }

    @Test
    void 회원_탈퇴_이벤트를_받으면_해당_회원의_관심과_알림만_삭제한다() {
        Policy policy = policy("WITHDRAW-POLICY");
        favorites.save(Favorite.builder().memberId(1L).policy(policy).build());
        favorites.save(Favorite.builder().memberId(2L).policy(policy).build());
        notifications.save(Notification.builder()
                .memberId(1L).policy(policy).deadlineDate(LocalDate.of(2026, 12, 31)).build());
        notifications.save(Notification.builder()
                .memberId(2L).policy(policy).deadlineDate(LocalDate.of(2026, 12, 31)).build());

        producer().send(MemberEventKafkaConfig.TOPIC, "1", event("withdraw-1", 1L));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(favorites.findAll()).extracting(Favorite::getMemberId).containsExactly(2L);
            assertThat(notifications.findAll()).extracting(Notification::getMemberId).containsExactly(2L);
        });
    }

    @Test
    void 같은_회원_탈퇴_이벤트를_반복해서_받아도_안전하다() {
        Policy policy = policy("IDEMPOTENT-POLICY");
        favorites.save(Favorite.builder().memberId(3L).policy(policy).build());

        KafkaTemplate<String, String> producer = producer();
        producer.send(MemberEventKafkaConfig.TOPIC, "3", event("withdraw-3", 3L));
        producer.send(MemberEventKafkaConfig.TOPIC, "3", event("withdraw-3", 3L));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    assertThat(favorites.findAll()).isEmpty();
                    assertThat(processedEvents.count()).isOne();
                    assertThat(processedEvents.findById("withdraw-3")).isPresent();
                });
        assertThat(failedEvents.count()).isZero();
    }

    @Test
    void 처리할_수_없는_메시지는_DLT로_보낸_뒤_DB에_보관한다() {
        producer().send(MemberEventKafkaConfig.TOPIC, "BAD", "{not-json");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(failedEvents.count()).isEqualTo(1);
            var failed = failedEvents.findAll().get(0);
            assertThat(failed.getEventKey()).isEqualTo("BAD");
            assertThat(failed.getPayload()).isEqualTo("{not-json");
            assertThat(failed.getDltTopic()).isEqualTo(MemberEventKafkaConfig.DLT);
            assertThat(failed.getOriginalTopic()).isEqualTo(MemberEventKafkaConfig.TOPIC);
            assertThat(failed.getOriginalPartition()).isZero();
            assertThat(failed.getOriginalOffset()).isNotNegative();
            assertThat(failed.getExceptionMessage()).isNotBlank();
            assertThat(failed.getResolvedAt()).isNull();
            assertThat(failedEvents.countByResolvedAtIsNull()).isOne();
        });
    }

    @Test
    void null_메시지도_DLT에서_막히지_않고_DB에_보관한다() {
        producer().send(MemberEventKafkaConfig.TOPIC, "NULL", null);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(failedEvents.count()).isEqualTo(1);
            var failed = failedEvents.findAll().get(0);
            assertThat(failed.getEventKey()).isEqualTo("NULL");
            assertThat(failed.getPayload()).isNull();
            assertThat(failed.getDltTopic()).isEqualTo(MemberEventKafkaConfig.DLT);
            assertThat(failed.getOriginalTopic()).isEqualTo(MemberEventKafkaConfig.TOPIC);
            assertThat(failed.getResolvedAt()).isNull();
        });
    }

    private Policy policy(String policyId) {
        Policy policy = new Policy();
        policy.setPolicyId(policyId);
        policy.setPolicyName("회원 탈퇴 테스트 정책");
        policy.setActiveYn(true);
        return policies.save(policy);
    }

    private KafkaTemplate<String, String> producer() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
    }

    private static String event(String eventId, long memberId) {
        return """
                {"eventId":"%s","eventType":"member-withdrawn","eventVersion":1,
                 "occurredAt":"2026-11-03T20:00:00+09:00","aggregateId":"%d","memberId":%d}
                """.formatted(eventId, memberId, memberId);
    }
}
