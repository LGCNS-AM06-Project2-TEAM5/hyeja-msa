package com.hyeja.userservice.domain.member.event;

public interface MemberWithdrawnPublisher {

    void publish(Long memberId);
}
