package com.hyeja.userservice.domain.member.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class MemberOutboxRelayTest {

    private final MemberOutboxRepository repository = mock(MemberOutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T05:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final MemberOutboxRelay relay = new MemberOutboxRelay(repository, kafkaTemplate, clock);

    @Test
    void 적은_순서대로_memberId를_key로_보내고_보낸_시각을_채운다() {
        MemberOutbox first = row("e-1", "1");
        MemberOutbox second = row("e-2", "2");
        given(repository.findTop100ByPublishedAtIsNullOrderByIdAsc()).willReturn(List.of(first, second));
        given(kafkaTemplate.send("member-withdrawn", "1", "{e-1}"))
                .willReturn(CompletableFuture.completedFuture(null));
        given(kafkaTemplate.send("member-withdrawn", "2", "{e-2}"))
                .willReturn(CompletableFuture.completedFuture(null));

        assertThat(relay.relayPending()).isEqualTo(2);

        assertThat(first.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 14, 0));
        assertThat(second.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 14, 0));
        verify(repository).save(first);
        verify(repository).save(second);
    }

    @Test
    void 전송이_실패하면_그_행에서_멈추고_다음_주기로_넘긴다() {
        MemberOutbox first = row("e-1", "1");
        MemberOutbox second = row("e-2", "2");
        given(repository.findTop100ByPublishedAtIsNullOrderByIdAsc()).willReturn(List.of(first, second));
        given(kafkaTemplate.send("member-withdrawn", "1", "{e-1}"))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThat(relay.relayPending()).isZero();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
        verify(repository, never()).save(first);
        verify(kafkaTemplate, never()).send("member-withdrawn", "2", "{e-2}");
    }

    private static MemberOutbox row(String eventId, String memberId) {
        return new MemberOutbox(
                eventId,
                memberId,
                "member-withdrawn",
                "{" + eventId + "}",
                LocalDateTime.of(2026, 11, 3, 13, 0)
        );
    }
}
