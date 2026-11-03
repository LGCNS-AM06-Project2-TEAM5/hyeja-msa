package com.hyeja.userservice.domain.member.event.outbox;

import com.hyeja.userservice.domain.member.event.MemberWithdrawnEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

// 커밋된 member_outbox 행을 순서대로 member-withdrawn 토픽에 보냅니다. key는 memberId입니다.
// 전송 후 published_at 기록 전에 중단되면 같은 eventId가 다시 나갈 수 있으므로 소비자가 중복을 제거합니다.
// 한 행이라도 실패하면 다음 주기에 그 행부터 다시 시도합니다.
@Slf4j
@RequiredArgsConstructor
public class MemberOutboxRelay implements AutoCloseable {

    static final String TOPIC = MemberWithdrawnEvent.TYPE;
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final MemberOutboxRepository memberOutboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    public int relayPending() {
        int sent = 0;
        for (MemberOutbox row : memberOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc()) {
            try {
                kafkaTemplate.send(TOPIC, row.getAggregateId(), row.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return sent;
            } catch (Exception exception) {
                log.warn("member-withdrawn 전송 실패, 다음 주기에 다시 보냅니다. eventId={}, memberId={}",
                        row.getEventId(), row.getAggregateId(), exception);
                return sent;
            }

            row.markPublished(LocalDateTime.now(clock));
            memberOutboxRepository.save(row);
            sent++;
        }

        if (sent > 0) {
            log.info("member-withdrawn {}건 전송", sent);
        }
        return sent;
    }

    @Override
    public void close() {
        kafkaTemplate.destroy();
    }
}
