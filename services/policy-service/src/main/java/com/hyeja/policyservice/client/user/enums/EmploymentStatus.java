package com.hyeja.policyservice.client.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// user-service Profile enum과 같은 이름입니다(F6 응답을 그대로 받음).
@Getter
@RequiredArgsConstructor
public enum EmploymentStatus {
    EMPLOYED("재직자"),
    SELF_EMPLOYED("자영업자"),
    UNEMPLOYED("미취업자"),
    FREELANCER("프리랜서"),
    DAILY_WORKER("일용근로자"),
    ENTREPRENEUR("(예비)창업자"),
    SHORT_TERM_WORKER("단기근로자"),
    FARMER("영농종사자"),
    OTHER("기타");

    private final String label;
}
