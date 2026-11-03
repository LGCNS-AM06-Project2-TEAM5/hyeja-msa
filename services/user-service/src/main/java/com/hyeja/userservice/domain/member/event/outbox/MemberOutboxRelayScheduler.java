package com.hyeja.userservice.domain.member.event.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class MemberOutboxRelayScheduler {

    private final MemberOutboxRelay relay;

    @Scheduled(initialDelayString = "${member.outbox.relay-interval-ms:1000}",
            fixedDelayString = "${member.outbox.relay-interval-ms:1000}")
    public void relayPending() {
        relay.relayPending();
    }
}
