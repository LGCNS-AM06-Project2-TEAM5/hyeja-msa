package com.hyeja.userservice.domain.member.event.outbox;

import com.hyeja.userservice.domain.member.event.MemberWithdrawnEvent;
import com.hyeja.userservice.domain.member.event.MemberWithdrawnPublisher;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// 회원 탈퇴 트랜잭션 안에서 member-withdrawn을 Outbox에 기록합니다.
// Member·Profile 변경과 함께 커밋·롤백되므로 탈퇴만 저장되고 이벤트가 빠지는 일을 막습니다.
@Component
@RequiredArgsConstructor
public class OutboxMemberWithdrawnPublisher implements MemberWithdrawnPublisher {

    private final MemberOutboxRepository memberOutboxRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    @Transactional
    public void publish(Long memberId) {
        String eventId = UUID.randomUUID().toString();
        OffsetDateTime occurredAt = OffsetDateTime.now(clock);
        MemberWithdrawnEvent event = MemberWithdrawnEvent.of(eventId, occurredAt, memberId);
        String payload = jsonMapper.writeValueAsString(event);

        memberOutboxRepository.save(new MemberOutbox(
                eventId,
                event.aggregateId(),
                MemberWithdrawnEvent.TYPE,
                payload,
                occurredAt.toLocalDateTime()
        ));
    }
}
