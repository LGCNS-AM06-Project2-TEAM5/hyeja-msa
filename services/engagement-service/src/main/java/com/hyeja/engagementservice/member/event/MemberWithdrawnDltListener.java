package com.hyeja.engagementservice.member.event;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class MemberWithdrawnDltListener {

    private final FailedMemberEventRepository repository;
    private final DltNotificationService notificationService;

    @Transactional
    @KafkaListener(
            topics = MemberEventKafkaConfig.DLT,
            groupId = MemberEventKafkaConfig.DLT_GROUP_ID,
            containerFactory = "memberWithdrawnDltListenerContainerFactory")
    public void store(ConsumerRecord<String, String> record) {
        if (repository.existsByDltTopicAndDltPartitionAndDltOffset(
                record.topic(), record.partition(), record.offset())) {
            return;
        }

        repository.save(new FailedMemberEvent(
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value(),
                textHeader(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                intHeader(record, KafkaHeaders.DLT_ORIGINAL_PARTITION),
                longHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
                abbreviate(textHeader(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE), 1000),
                LocalDateTime.now()));
        notificationService.recordFailure(record.topic(), record.key(), record.value());
        log.error("member-withdrawn DLT 저장. key={}, partition={}, offset={}",
                record.key(), record.partition(), record.offset());
    }

    private String textHeader(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private Integer intHeader(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : ByteBuffer.wrap(header.value()).getInt();
    }

    private Long longHeader(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : ByteBuffer.wrap(header.value()).getLong();
    }

    private String abbreviate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
