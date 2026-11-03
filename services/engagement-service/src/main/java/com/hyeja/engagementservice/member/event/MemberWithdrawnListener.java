package com.hyeja.engagementservice.member.event;

import com.hyeja.engagementservice.member.service.MemberEngagementCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class MemberWithdrawnListener {

    private final JsonMapper jsonMapper;
    private final MemberEngagementCleanupService cleanupService;

    @KafkaListener(
            topics = MemberEventKafkaConfig.TOPIC,
            groupId = MemberEventKafkaConfig.GROUP_ID,
            containerFactory = "memberWithdrawnListenerContainerFactory")
    public void onMemberWithdrawn(ConsumerRecord<String, String> record) {
        MemberWithdrawnEvent event = jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class);
        event.validate();
        if (record.key() != null && !event.aggregateId().equals(record.key())) {
            throw new IllegalArgumentException("Kafka key와 aggregateId가 일치하지 않습니다.");
        }

        var result = cleanupService.cleanup(event.memberId());
        log.info("회원 탈퇴 데이터 정리 완료. eventId={}, memberId={}, favorites={}, notifications={}",
                event.eventId(), event.memberId(), result.deletedFavorites(), result.deletedNotifications());
    }
}
